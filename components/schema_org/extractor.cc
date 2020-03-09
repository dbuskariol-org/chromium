// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "components/schema_org/extractor.h"

#include <algorithm>
#include <memory>
#include <unordered_set>
#include <utility>
#include <vector>

#include "base/json/json_parser.h"
#include "base/strings/string_number_conversions.h"
#include "base/values.h"
#include "components/schema_org/common/metadata.mojom.h"
#include "components/schema_org/schema_org_entity_names.h"
#include "components/schema_org/schema_org_property_configurations.h"
#include "components/schema_org/validator.h"

namespace schema_org {

namespace {

// App Indexing enforces a max nesting depth of 5. Our top level message
// corresponds to the WebPage, so this only leaves 4 more levels. We will parse
// entities up to this depth, and ignore any further nesting. If an object at
// the max nesting depth has a property corresponding to an entity, that
// property will be dropped. Note that we will still parse json-ld blocks deeper
// than this, but it won't be passed to App Indexing.
constexpr int kMaxDepth = 5;
// Some strings are very long, and we don't currently use those, so limit string
// length to something reasonable to avoid undue pressure on Icing. Note that
// App Indexing supports strings up to length 20k.
constexpr size_t kMaxStringLength = 200;
// Enforced by App Indexing, so stop processing early if possible.
constexpr size_t kMaxNumFields = 25;
// Enforced by App Indexing, so stop processing early if possible.
constexpr size_t kMaxRepeatedSize = 100;

constexpr char kJSONLDKeyType[] = "@type";

const std::unordered_set<std::string> kSupportedTypes{
    entity::kVideoObject, entity::kMovie, entity::kTVEpisode, entity::kTVSeason,
    entity::kTVSeries};
bool IsSupportedType(const std::string& type) {
  return kSupportedTypes.find(type) != kSupportedTypes.end();
}

void ExtractEntity(base::DictionaryValue*, mojom::Entity&, int recursion_level);

// Initializes a vector of the appropriate type for the property.
bool InitializeStringValue(const std::string& property_type,
                           mojom::Values* values) {
  schema_org::property::PropertyConfiguration prop_config =
      schema_org::property::GetPropertyConfiguration(property_type);
  if (prop_config.text) {
    values->set_string_values(std::vector<std::string>());
  } else if (prop_config.number) {
    values->set_double_values(std::vector<double>());
  } else if (prop_config.date_time || prop_config.date) {
    values->set_date_time_values(std::vector<base::Time>());
  } else if (prop_config.time) {
    values->set_time_values(std::vector<base::TimeDelta>());
  } else {
    return false;
  }

  return true;
}

// Parses a string into a property value. The string may be parsed as a double,
// date, or time, depending on the types that the property supports. If the
// property supports text, uses the string itself.
bool ParseStringValue(const std::string& property_type,
                      base::StringPiece value,
                      mojom::Values* values) {
  value = value.substr(0, kMaxStringLength);

  schema_org::property::PropertyConfiguration prop_config =
      schema_org::property::GetPropertyConfiguration(property_type);
  if (prop_config.text) {
    values->get_string_values().push_back(value.as_string());
    return true;
  }
  if (prop_config.number) {
    double d;
    bool parsed_double = base::StringToDouble(value, &d);
    if (parsed_double) {
      values->get_double_values().push_back(d);
      return true;
    }
  }
  if (prop_config.date_time || prop_config.date) {
    base::Time time;
    bool parsed_time = base::Time::FromString(value.data(), &time);
    if (parsed_time) {
      values->get_date_time_values().push_back(time);
      return true;
    }
  }
  if (prop_config.time) {
    base::Time time_of_day;
    base::Time start_of_day;
    bool parsed_time = base::Time::FromString(
        ("1970-01-01T" + value.as_string()).c_str(), &time_of_day);
    bool parsed_day_start =
        base::Time::FromString("1970-01-01T00:00:00", &start_of_day);
    base::TimeDelta time = time_of_day - start_of_day;
    // The string failed to parse as a DateTime, but did parse as a Time. Use
    // this value, initializing the vector first. (We cannot initialize it in
    // advance like the others, because we do not know if parsing will work in
    // advance.)
    if (parsed_time && parsed_day_start) {
      if (!values->is_time_values()) {
        values->set_time_values(std::vector<base::TimeDelta>());
      }
      values->get_time_values().push_back(time);
      return true;
    }
  }
  return false;
}

// Parses a property with multiple string values and places the result in
// values. This will be an array of a single type, depending on what kinds of
// types the property supports.
bool ParseRepeatedString(const base::Value::ListView& arr,
                         const std::string& property_type,
                         mojom::Values* values) {
  if (!InitializeStringValue(property_type, values)) {
    return false;
  }

  for (size_t j = 0; j < std::min(arr.size(), kMaxRepeatedSize); ++j) {
    auto& list_item = arr[j];
    if (list_item.type() != base::Value::Type::STRING) {
      return false;
    }
    base::StringPiece v = list_item.GetString();
    if (!ParseStringValue(property_type, v, values)) {
      return false;
    }
  }

  return true;
}

// Parses a repeated property value and places the result in values. The result
// will be an array of a single type.
bool ParseRepeatedValue(base::Value::ListView& arr,
                        const std::string& property_type,
                        mojom::Values* values,
                        int recursion_level) {
  if (arr.empty()) {
    return false;
  }

  bool is_first_item = true;
  base::Value::Type type = base::Value::Type::NONE;

  for (size_t j = 0; j < std::min(arr.size(), kMaxRepeatedSize); ++j) {
    auto& list_item = arr[j];
    if (is_first_item) {
      is_first_item = false;
      type = list_item.type();
      switch (type) {
        case base::Value::Type::BOOLEAN:
          values->set_bool_values(std::vector<bool>());
          break;
        case base::Value::Type::INTEGER:
          values->set_long_values(std::vector<int64_t>());
          break;
        case base::Value::Type::DOUBLE:
          // App Indexing doesn't support double type, so just encode its
          // decimal value as a string instead.
          values->set_string_values(std::vector<std::string>());
          break;
        case base::Value::Type::STRING:
          return ParseRepeatedString(arr, property_type, values);
        case base::Value::Type::DICTIONARY:
          if (recursion_level + 1 >= kMaxDepth) {
            return false;
          }
          values->set_entity_values(std::vector<mojom::EntityPtr>());
          break;
        case base::Value::Type::LIST:
          // App Indexing doesn't support nested arrays.
          return false;
        default:
          // Unknown value type.
          return false;
      }
    }

    if (list_item.type() != type) {
      // App Indexing doesn't support mixed types. If there are mixed
      // types in the parsed object, we will drop the property.
      return false;
    }
    switch (list_item.type()) {
      case base::Value::Type::BOOLEAN: {
        bool v;
        list_item.GetAsBoolean(&v);
        values->get_bool_values().push_back(v);
      } break;
      case base::Value::Type::INTEGER: {
        int v = list_item.GetInt();
        values->get_long_values().push_back(v);
      } break;
      case base::Value::Type::DOUBLE: {
        // App Indexing doesn't support double type, so just encode its decimal
        // value as a string instead.
        double v = list_item.GetDouble();
        std::string s = base::NumberToString(v);
        s = s.substr(0, kMaxStringLength);
        values->get_string_values().push_back(std::move(s));
      } break;
      case base::Value::Type::DICTIONARY: {
        values->get_entity_values().push_back(mojom::Entity::New());

        base::DictionaryValue* dict_value = nullptr;
        if (list_item.GetAsDictionary(&dict_value)) {
          ExtractEntity(dict_value, *(values->get_entity_values().at(j)),
                        recursion_level + 1);
        }
      } break;
      default:
        break;
    }
  }
  return true;
}

void ExtractEntity(base::DictionaryValue* val,
                   mojom::Entity& entity,
                   int recursion_level) {
  if (recursion_level >= kMaxDepth) {
    return;
  }

  std::string type = "";
  val->GetString(kJSONLDKeyType, &type);
  if (type == "") {
    type = "Thing";
  }
  entity.type = type;
  for (const auto& entry : val->DictItems()) {
    if (entity.properties.size() >= kMaxNumFields) {
      break;
    }
    mojom::PropertyPtr property = mojom::Property::New();
    property->name = entry.first;
    if (property->name == kJSONLDKeyType) {
      continue;
    }
    property->values = mojom::Values::New();

    switch (entry.second.type()) {
      case base::Value::Type::BOOLEAN:
        property->values->set_bool_values({entry.second.GetBool()});
        break;
      case base::Value::Type::INTEGER:
        property->values->set_long_values({entry.second.GetInt()});
        break;
      case base::Value::Type::DOUBLE:
        property->values->set_double_values({entry.second.GetDouble()});
        break;
      case base::Value::Type::STRING: {
        base::StringPiece v = entry.second.GetString();
        if (!(InitializeStringValue(property->name, property->values.get()) &&
              ParseStringValue(property->name, v, property->values.get()))) {
          continue;
        }
        break;
      }
      case base::Value::Type::DICTIONARY: {
        if (recursion_level + 1 >= kMaxDepth) {
          continue;
        }
        property->values->set_entity_values(std::vector<mojom::EntityPtr>());
        property->values->get_entity_values().push_back(mojom::Entity::New());

        base::DictionaryValue* dict_value = nullptr;
        if (!entry.second.GetAsDictionary(&dict_value)) {
          continue;
        }
        ExtractEntity(dict_value,
                      *(property->values->get_entity_values().at(0)),
                      recursion_level + 1);
        break;
      }
      case base::Value::Type::LIST: {
        base::Value::ListView list_view = entry.second.GetList();
        if (!ParseRepeatedValue(list_view, property->name,
                                property->values.get(), recursion_level)) {
          continue;
        }
        break;
      }
      default: {
        // Unsupported value type. Skip this property.
        continue;
      }
    }

    entity.properties.push_back(std::move(property));
  }
}

// Extract a JSONObject which corresponds to a single (possibly nested) entity.
mojom::EntityPtr ExtractTopLevelEntity(base::DictionaryValue* val) {
  mojom::EntityPtr entity = mojom::Entity::New();
  std::string type;
  val->GetString(kJSONLDKeyType, &type);
  if (!IsSupportedType(type)) {
    return nullptr;
  }
  ExtractEntity(val, *entity, 0);
  return entity;
}

}  // namespace

mojom::EntityPtr Extractor::Extract(const std::string& content) {
  base::Optional<base::Value> value(base::JSONReader::Read(content));
  base::DictionaryValue* dict_value = nullptr;

  if (!value || !value.value().GetAsDictionary(&dict_value)) {
    return nullptr;
  }

  mojom::EntityPtr entity = ExtractTopLevelEntity(dict_value);

  bool is_valid = false;
  if (!entity.is_null()) {
    is_valid = ValidateEntity(entity.get());
  }

  return is_valid ? std::move(entity) : nullptr;
}

}  // namespace schema_org
