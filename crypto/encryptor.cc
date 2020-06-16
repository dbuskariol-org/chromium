// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "crypto/encryptor.h"

#include <stddef.h>
#include <stdint.h>

#include "base/logging.h"
#include "base/strings/string_util.h"
#include "base/sys_byteorder.h"
#include "crypto/openssl_util.h"
#include "crypto/symmetric_key.h"
#include "third_party/boringssl/src/include/openssl/aes.h"
#include "third_party/boringssl/src/include/openssl/evp.h"

namespace crypto {

namespace {

const EVP_CIPHER* GetCipherForKey(const SymmetricKey* key) {
  switch (key->key().length()) {
    case 16: return EVP_aes_128_cbc();
    case 32: return EVP_aes_256_cbc();
    default:
      return nullptr;
  }
}

// On destruction this class will cleanup the ctx, and also clear the OpenSSL
// ERR stack as a convenience.
class ScopedCipherCTX {
 public:
  ScopedCipherCTX() {
    EVP_CIPHER_CTX_init(&ctx_);
  }
  ~ScopedCipherCTX() {
    EVP_CIPHER_CTX_cleanup(&ctx_);
    ClearOpenSSLERRStack(FROM_HERE);
  }
  EVP_CIPHER_CTX* get() { return &ctx_; }

 private:
  EVP_CIPHER_CTX ctx_;
};

}  // namespace

/////////////////////////////////////////////////////////////////////////////
// Encyptor::Counter Implementation.
Encryptor::Counter::Counter(base::span<const uint8_t> counter) {
  CHECK(sizeof(counter_) == counter.size());

  memcpy(&counter_, counter.data(), sizeof(counter_));
}

Encryptor::Counter::~Counter() = default;

bool Encryptor::Counter::Increment() {
  uint64_t low_num = base::NetToHost64(counter_.components64[1]);
  uint64_t new_low_num = low_num + 1;
  counter_.components64[1] = base::HostToNet64(new_low_num);

  // If overflow occured then increment the most significant component.
  if (new_low_num < low_num) {
    counter_.components64[0] =
        base::HostToNet64(base::NetToHost64(counter_.components64[0]) + 1);
  }

  // TODO(hclam): Return false if counter value overflows.
  return true;
}

void Encryptor::Counter::Write(void* buf) {
  uint8_t* buf_ptr = reinterpret_cast<uint8_t*>(buf);
  memcpy(buf_ptr, &counter_, sizeof(counter_));
}

size_t Encryptor::Counter::GetLengthInBytes() const {
  return sizeof(counter_);
}

/////////////////////////////////////////////////////////////////////////////
// Encryptor Implementation.

Encryptor::Encryptor() : key_(nullptr), mode_(CBC) {}

Encryptor::~Encryptor() = default;

bool Encryptor::Init(const SymmetricKey* key, Mode mode, base::StringPiece iv) {
  return Init(key, mode, base::as_bytes(base::make_span(iv)));
}

bool Encryptor::Init(const SymmetricKey* key,
                     Mode mode,
                     base::span<const uint8_t> iv) {
  DCHECK(key);
  DCHECK(mode == CBC || mode == CTR);

  EnsureOpenSSLInit();
  if (mode == CBC && iv.size() != AES_BLOCK_SIZE)
    return false;

  if (GetCipherForKey(key) == nullptr)
    return false;

  key_ = key;
  mode_ = mode;
  iv_.assign(iv.begin(), iv.end());
  return true;
}

bool Encryptor::Encrypt(base::StringPiece plaintext, std::string* ciphertext) {
  CHECK(!plaintext.empty() || mode_ == CBC);
  return CryptString(/*do_encrypt=*/true, plaintext, ciphertext);
}

bool Encryptor::Encrypt(base::span<const uint8_t> plaintext,
                        std::vector<uint8_t>* ciphertext) {
  CHECK(!plaintext.empty() || mode_ == CBC);
  return CryptBytes(/*do_encrypt=*/true, plaintext, ciphertext);
}

bool Encryptor::Decrypt(base::StringPiece ciphertext, std::string* plaintext) {
  CHECK(!ciphertext.empty());
  return CryptString(/*do_encrypt=*/false, ciphertext, plaintext);
}

bool Encryptor::Decrypt(base::span<const uint8_t> ciphertext,
                        std::vector<uint8_t>* plaintext) {
  CHECK(!ciphertext.empty());
  return CryptBytes(/*do_encrypt=*/false, ciphertext, plaintext);
}

bool Encryptor::SetCounter(base::StringPiece counter) {
  return SetCounter(base::as_bytes(base::make_span(counter)));
}

bool Encryptor::SetCounter(base::span<const uint8_t> counter) {
  if (mode_ != CTR)
    return false;
  if (counter.size() != 16u)
    return false;

  counter_ = std::make_unique<Counter>(counter);
  return true;
}

bool Encryptor::CryptString(bool do_encrypt,
                            base::StringPiece input,
                            std::string* output) {
  size_t out_size = MaxOutput(do_encrypt, input.size());
  CHECK_GT(out_size + 1, out_size);  // Overflow
  std::string result;
  uint8_t* out_ptr =
      reinterpret_cast<uint8_t*>(base::WriteInto(&result, out_size + 1));

  base::Optional<size_t> len =
      (mode_ == CTR)
          ? CryptCTR(do_encrypt, base::as_bytes(base::make_span(input)),
                     base::make_span(out_ptr, out_size))
          : Crypt(do_encrypt, base::as_bytes(base::make_span(input)),
                  base::make_span(out_ptr, out_size));
  if (!len)
    return false;

  result.resize(*len);
  *output = std::move(result);
  return true;
}

bool Encryptor::CryptBytes(bool do_encrypt,
                           base::span<const uint8_t> input,
                           std::vector<uint8_t>* output) {
  std::vector<uint8_t> result(MaxOutput(do_encrypt, input.size()));
  base::Optional<size_t> len = (mode_ == CTR)
                                   ? CryptCTR(do_encrypt, input, result)
                                   : Crypt(do_encrypt, input, result);
  if (!len)
    return false;

  result.resize(*len);
  *output = std::move(result);
  return true;
}

size_t Encryptor::MaxOutput(bool do_encrypt, size_t length) {
  size_t result = length + ((do_encrypt && mode_ == CBC) ? 16 : 0);
  CHECK_GE(result, length);  // Overflow
  return result;
}

base::Optional<size_t> Encryptor::Crypt(bool do_encrypt,
                                        base::span<const uint8_t> input,
                                        base::span<uint8_t> output) {
  DCHECK(key_);  // Must call Init() before En/De-crypt.

  const EVP_CIPHER* cipher = GetCipherForKey(key_);
  DCHECK(cipher);  // Already handled in Init();

  const std::string& key = key_->key();
  DCHECK_EQ(EVP_CIPHER_iv_length(cipher), iv_.size());
  DCHECK_EQ(EVP_CIPHER_key_length(cipher), key.size());

  ScopedCipherCTX ctx;
  if (!EVP_CipherInit_ex(ctx.get(), cipher, nullptr,
                         reinterpret_cast<const uint8_t*>(key.data()),
                         iv_.data(), do_encrypt)) {
    return base::nullopt;
  }

  // Encrypting needs a block size of space to allow for any padding.
  CHECK_GE(output.size(), input.size() + (do_encrypt ? iv_.size() : 0));
  int out_len;
  if (!EVP_CipherUpdate(ctx.get(), output.data(), &out_len, input.data(),
                        input.size()))
    return base::nullopt;

  // Write out the final block plus padding (if any) to the end of the data
  // just written.
  int tail_len;
  if (!EVP_CipherFinal_ex(ctx.get(), output.data() + out_len, &tail_len))
    return base::nullopt;

  out_len += tail_len;
  DCHECK_LE(out_len, static_cast<int>(output.size()));
  return out_len;
}

base::Optional<size_t> Encryptor::CryptCTR(bool do_encrypt,
                                           base::span<const uint8_t> input,
                                           base::span<uint8_t> output) {
  if (!counter_.get()) {
    LOG(ERROR) << "Counter value not set in CTR mode.";
    return base::nullopt;
  }

  AES_KEY aes_key;
  if (AES_set_encrypt_key(reinterpret_cast<const uint8_t*>(key_->key().data()),
                          key_->key().size() * 8, &aes_key) != 0) {
    return base::nullopt;
  }

  uint8_t ivec[AES_BLOCK_SIZE] = { 0 };
  uint8_t ecount_buf[AES_BLOCK_SIZE] = { 0 };
  unsigned int block_offset = 0;

  counter_->Write(ivec);

  // |output| must have room for |input|.
  CHECK_GE(output.size(), input.size());
  AES_ctr128_encrypt(input.data(), output.data(), input.size(), &aes_key, ivec,
                     ecount_buf, &block_offset);

  // AES_ctr128_encrypt() updates |ivec|. Update the |counter_| here.
  SetCounter(ivec);
  return input.size();
}

}  // namespace crypto
