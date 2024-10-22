// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef UI_BASE_X_X11_UTIL_H_
#define UI_BASE_X_X11_UTIL_H_

// This file declares utility functions for X11 (Linux only).
//
// These functions do not require the Xlib headers to be included (which is why
// we use a void* for Visual*). The Xlib headers are highly polluting so we try
// hard to limit their spread into the rest of the code.

#include <stddef.h>

#include <memory>
#include <string>
#include <vector>

#include "base/component_export.h"
#include "base/containers/flat_set.h"
#include "base/macros.h"
#include "base/memory/ref_counted_memory.h"
#include "base/memory/scoped_refptr.h"
#include "ui/base/cursor/mojom/cursor_type.mojom-forward.h"
#include "ui/events/event_constants.h"
#include "ui/events/keycodes/keyboard_codes.h"
#include "ui/events/platform_event.h"
#include "ui/gfx/icc_profile.h"
#include "ui/gfx/x/event.h"
#include "ui/gfx/x/x11_types.h"
#include "ui/gfx/x/xproto_types.h"

typedef unsigned long Cursor;

namespace gfx {
class Insets;
class Point;
class Rect;
}  // namespace gfx
class SkBitmap;

namespace ui {

enum WmState : uint32_t {
  WM_STATE_WITHDRAWN = 0,
  WM_STATE_NORMAL = 1,
  WM_STATE_ICONIC = 3,
};

enum SizeHintsFlags : int32_t {
  SIZE_HINT_US_POSITION = 1 << 0,
  SIZE_HINT_US_SIZE = 1 << 1,
  SIZE_HINT_P_POSITION = 1 << 2,
  SIZE_HINT_P_SIZE = 1 << 3,
  SIZE_HINT_P_MIN_SIZE = 1 << 4,
  SIZE_HINT_P_MAX_SIZE = 1 << 5,
  SIZE_HINT_P_RESIZE_INC = 1 << 6,
  SIZE_HINT_P_ASPECT = 1 << 7,
  SIZE_HINT_BASE_SIZE = 1 << 8,
  SIZE_HINT_P_WIN_GRAVITY = 1 << 9,
};

struct SizeHints {
  // User specified flags
  int32_t flags;
  // User-specified position
  int32_t x, y;
  // User-specified size
  int32_t width, height;
  // Program-specified minimum size
  int32_t min_width, min_height;
  // Program-specified maximum size
  int32_t max_width, max_height;
  // Program-specified resize increments
  int32_t width_inc, height_inc;
  // Program-specified minimum aspect ratios
  int32_t min_aspect_num, min_aspect_den;
  // Program-specified maximum aspect ratios
  int32_t max_aspect_num, max_aspect_den;
  // Program-specified base size
  int32_t base_width, base_height;
  // Program-specified window gravity
  uint32_t win_gravity;
};

enum WmHintsFlags : uint32_t {
  WM_HINT_INPUT = 1L << 0,
  WM_HINT_STATE = 1L << 1,
  WM_HINT_ICON_PIXMAP = 1L << 2,
  WM_HINT_ICON_WINDOW = 1L << 3,
  WM_HINT_ICON_POSITION = 1L << 4,
  WM_HINT_ICON_MASK = 1L << 5,
  WM_HINT_WINDOW_GROUP = 1L << 6,
  // 1L << 7 doesn't have any defined meaning
  WM_HINT_X_URGENCY = 1L << 8
};

struct WmHints {
  // Marks which fields in this structure are defined
  int32_t flags;
  // Does this application rely on the window manager to get keyboard input?
  uint32_t input;
  // See below
  int32_t initial_state;
  // Pixmap to be used as icon
  xcb_pixmap_t icon_pixmap;
  // Window to be used as icon
  xcb_window_t icon_window;
  // Initial position of icon
  int32_t icon_x, icon_y;
  // Icon mask bitmap
  xcb_pixmap_t icon_mask;
  // Identifier of related window group
  xcb_window_t window_group;
};

// These functions use the default display and this /must/ be called from
// the UI thread. Thus, they don't support multiple displays.

template <typename T>
bool GetArrayProperty(x11::Window window,
                      x11::Atom name,
                      std::vector<T>* value,
                      x11::Atom* out_type = nullptr,
                      size_t amount = 0) {
  static_assert(sizeof(T) == 1 || sizeof(T) == 2 || sizeof(T) == 4, "");

  size_t bytes = amount * sizeof(T);
  // The length field specifies the maximum amount of data we would like the
  // server to give us.  It's specified in units of 4 bytes, so divide by 4.
  // Add 3 before division to round up.
  size_t length = (bytes + 3) / 4;
  using lentype = decltype(x11::GetPropertyRequest::long_length);
  auto response =
      x11::Connection::Get()
          ->GetProperty(
              {.window = static_cast<x11::Window>(window),
               .property = name,
               .long_length =
                   amount ? length : std::numeric_limits<lentype>::max()})
          .Sync();
  if (!response || response->format != CHAR_BIT * sizeof(T))
    return false;

  DCHECK_EQ(response->format / CHAR_BIT * response->value_len,
            response->value->size());
  value->resize(response->value_len);
  memcpy(value->data(), response->value->data(), response->value->size());
  if (out_type)
    *out_type = response->type;
  return true;
}

template <typename T>
bool GetProperty(x11::Window window, const x11::Atom name, T* value) {
  std::vector<T> values;
  if (!GetArrayProperty(window, name, &values, nullptr, 1) || values.empty())
    return false;
  *value = values[0];
  return true;
}

template <typename T>
void SetArrayProperty(x11::Window window,
                      x11::Atom name,
                      x11::Atom type,
                      const std::vector<T>& values) {
  static_assert(sizeof(T) == 1 || sizeof(T) == 2 || sizeof(T) == 4, "");
  std::vector<uint8_t> data(sizeof(T) * values.size());
  memcpy(data.data(), values.data(), sizeof(T) * values.size());
  x11::Connection::Get()->ChangeProperty(
      {.window = static_cast<x11::Window>(window),
       .property = name,
       .type = type,
       .format = CHAR_BIT * sizeof(T),
       .data_len = values.size(),
       .data = base::RefCountedBytes::TakeVector(&data)});
}

template <typename T>
void SetProperty(x11::Window window,
                 x11::Atom name,
                 x11::Atom type,
                 const T& value) {
  SetArrayProperty(window, name, type, std::vector<T>{value});
}

template <typename T>
x11::Future<void> SendEvent(const T& event,
                            x11::Window target,
                            x11::EventMask mask) {
  static_assert(T::type_id > 0, "T must be an x11::*Event type");
  auto write_buffer = x11::Write(event);
  DCHECK_EQ(write_buffer.GetBuffers().size(), 1ul);
  auto& first_buffer = write_buffer.GetBuffers()[0];
  DCHECK_LE(first_buffer->size(), 32ul);
  std::vector<uint8_t> event_bytes(32);
  memcpy(event_bytes.data(), first_buffer->data(), first_buffer->size());

  x11::SendEventRequest send_event{false, target, mask};
  std::copy(event_bytes.begin(), event_bytes.end(), send_event.event.begin());
  return x11::Connection::Get()->SendEvent(send_event);
}

COMPONENT_EXPORT(UI_BASE_X)
void DeleteProperty(x11::Window window, x11::Atom name);

COMPONENT_EXPORT(UI_BASE_X)
bool GetWmNormalHints(x11::Window window, SizeHints* hints);

COMPONENT_EXPORT(UI_BASE_X)
void SetWmNormalHints(x11::Window window, const SizeHints& hints);

COMPONENT_EXPORT(UI_BASE_X)
bool GetWmHints(x11::Window window, WmHints* hints);

COMPONENT_EXPORT(UI_BASE_X)
void SetWmHints(x11::Window window, const WmHints& hints);

COMPONENT_EXPORT(UI_BASE_X)
void WithdrawWindow(x11::Window window);

COMPONENT_EXPORT(UI_BASE_X)
void RaiseWindow(x11::Window window);

COMPONENT_EXPORT(UI_BASE_X)
void LowerWindow(x11::Window window);

COMPONENT_EXPORT(UI_BASE_X)
void DefineCursor(x11::Window window, x11::Cursor cursor);

COMPONENT_EXPORT(UI_BASE_X)
x11::Window CreateDummyWindow(const std::string& name = "");

COMPONENT_EXPORT(UI_BASE_X)
x11::KeyCode KeysymToKeycode(x11::Connection* connection, x11::KeySym keysym);

// These functions cache their results ---------------------------------

// Returns true if the system supports XINPUT2.
COMPONENT_EXPORT(UI_BASE_X) bool IsXInput2Available();

// Return true iff the display supports Xrender
COMPONENT_EXPORT(UI_BASE_X) bool QueryRenderSupport(XDisplay* dpy);

// Return true iff the display supports MIT-SHM.
COMPONENT_EXPORT(UI_BASE_X) bool QueryShmSupport();

// Returns the first event ID for the MIT-SHM extension, if available.
COMPONENT_EXPORT(UI_BASE_X) int ShmEventBase();

// Creates a custom X cursor from the image. This takes ownership of image. The
// caller must not free/modify the image. The refcount of the newly created
// cursor is set to 1.
COMPONENT_EXPORT(UI_BASE_X)::Cursor
    CreateReffedCustomXCursor(XcursorImage* image);

// Increases the refcount of the custom cursor.
COMPONENT_EXPORT(UI_BASE_X) void RefCustomXCursor(::Cursor cursor);

// Decreases the refcount of the custom cursor, and destroys it if it reaches 0.
COMPONENT_EXPORT(UI_BASE_X) void UnrefCustomXCursor(::Cursor cursor);

// Creates a XcursorImage and copies the SkBitmap |bitmap| on it. Caller owns
// the returned object.
COMPONENT_EXPORT(UI_BASE_X)
XcursorImage* SkBitmapToXcursorImage(const SkBitmap& bitmap,
                                     const gfx::Point& hotspot);

// Loads and returns an X11 cursor, trying to find one that matches |type|. If
// unavailable, x11::None is returned.
COMPONENT_EXPORT(UI_BASE_X)
::Cursor LoadCursorFromType(mojom::CursorType type);

// Coalesce all pending motion events (touch or mouse) that are at the top of
// the queue, and return the number eliminated, storing the last one in
// |last_event|.
COMPONENT_EXPORT(UI_BASE_X)
int CoalescePendingMotionEvents(const x11::Event* xev, x11::Event* last_event);

// Hides the host cursor.
COMPONENT_EXPORT(UI_BASE_X) void HideHostCursor();

// Returns an invisible cursor.
COMPONENT_EXPORT(UI_BASE_X)::Cursor CreateInvisibleCursor();

// Sets whether |window| should use the OS window frame.
COMPONENT_EXPORT(UI_BASE_X)
void SetUseOSWindowFrame(x11::Window window, bool use_os_window_frame);

// These functions do not cache their results --------------------------

// Returns true if the shape extension is supported.
COMPONENT_EXPORT(UI_BASE_X) bool IsShapeExtensionAvailable();

// Get the X window id for the default root window
COMPONENT_EXPORT(UI_BASE_X) x11::Window GetX11RootWindow();

// Returns the user's current desktop.
COMPONENT_EXPORT(UI_BASE_X) bool GetCurrentDesktop(int* desktop);

enum HideTitlebarWhenMaximized : uint32_t {
  SHOW_TITLEBAR_WHEN_MAXIMIZED = 0,
  HIDE_TITLEBAR_WHEN_MAXIMIZED = 1,
};
// Sets _GTK_HIDE_TITLEBAR_WHEN_MAXIMIZED on |window|.
COMPONENT_EXPORT(UI_BASE_X)
void SetHideTitlebarWhenMaximizedProperty(x11::Window window,
                                          HideTitlebarWhenMaximized property);

// Clears all regions of X11's default root window by filling black pixels.
COMPONENT_EXPORT(UI_BASE_X) void ClearX11DefaultRootWindow();

// Returns true if |window| is visible.
COMPONENT_EXPORT(UI_BASE_X) bool IsWindowVisible(x11::Window window);

// Returns the inner bounds of |window| (excluding the non-client area).
COMPONENT_EXPORT(UI_BASE_X)
bool GetInnerWindowBounds(x11::Window window, gfx::Rect* rect);

// Returns the non-client area extents of |window|. This is a negative inset; it
// represents the negative size of the window border on all sides.
// InnerWindowBounds.Inset(WindowExtents) = OuterWindowBounds.
// Returns false if the window manager does not provide extents information.
COMPONENT_EXPORT(UI_BASE_X)
bool GetWindowExtents(x11::Window window, gfx::Insets* extents);

// Returns the outer bounds of |window| (including the non-client area).
COMPONENT_EXPORT(UI_BASE_X)
bool GetOuterWindowBounds(x11::Window window, gfx::Rect* rect);

// Returns true if |window| contains the point |screen_loc|.
COMPONENT_EXPORT(UI_BASE_X)
bool WindowContainsPoint(x11::Window window, gfx::Point screen_loc);

// Return true if |window| has any property with |property_name|.
COMPONENT_EXPORT(UI_BASE_X)
bool PropertyExists(x11::Window window, const std::string& property_name);

// Returns the raw bytes from a property with minimal
// interpretation. |out_data| should be freed by XFree() after use.
COMPONENT_EXPORT(UI_BASE_X)
bool GetRawBytesOfProperty(x11::Window window,
                           x11::Atom property,
                           scoped_refptr<base::RefCountedMemory>* out_data,
                           x11::Atom* out_type);

// Get the value of an int, int array, atom array or string property.  On
// success, true is returned and the value is stored in |value|.
//
// These functions should no longer be used.  TODO(thomasanderson): migrate
// existing callers to {Set,Get}{,Array}Property<> instead.
COMPONENT_EXPORT(UI_BASE_X)
bool GetIntProperty(x11::Window window,
                    const std::string& property_name,
                    int32_t* value);
COMPONENT_EXPORT(UI_BASE_X)
bool GetIntArrayProperty(x11::Window window,
                         const std::string& property_name,
                         std::vector<int32_t>* value);
COMPONENT_EXPORT(UI_BASE_X)
bool GetAtomArrayProperty(x11::Window window,
                          const std::string& property_name,
                          std::vector<x11::Atom>* value);
COMPONENT_EXPORT(UI_BASE_X)
bool GetStringProperty(x11::Window window,
                       const std::string& property_name,
                       std::string* value);

COMPONENT_EXPORT(UI_BASE_X)
void SetIntProperty(x11::Window window,
                    const std::string& name,
                    const std::string& type,
                    int32_t value);
COMPONENT_EXPORT(UI_BASE_X)
void SetIntArrayProperty(x11::Window window,
                         const std::string& name,
                         const std::string& type,
                         const std::vector<int32_t>& value);
COMPONENT_EXPORT(UI_BASE_X)
void SetAtomProperty(x11::Window window,
                     const std::string& name,
                     const std::string& type,
                     x11::Atom value);
COMPONENT_EXPORT(UI_BASE_X)
void SetAtomArrayProperty(x11::Window window,
                          const std::string& name,
                          const std::string& type,
                          const std::vector<x11::Atom>& value);
COMPONENT_EXPORT(UI_BASE_X)
void SetStringProperty(x11::Window window,
                       x11::Atom property,
                       x11::Atom type,
                       const std::string& value);

// Sets the WM_CLASS attribute for a given X11 window.
COMPONENT_EXPORT(UI_BASE_X)
void SetWindowClassHint(x11::Connection* connection,
                        x11::Window window,
                        const std::string& res_name,
                        const std::string& res_class);

// Sets the WM_WINDOW_ROLE attribute for a given X11 window.
COMPONENT_EXPORT(UI_BASE_X)
void SetWindowRole(x11::Window window, const std::string& role);

// Sends a message to the x11 window manager, enabling or disabling the
// states |state1| and |state2|.
COMPONENT_EXPORT(UI_BASE_X)
void SetWMSpecState(x11::Window window,
                    bool enabled,
                    x11::Atom state1,
                    x11::Atom state2);

// Sends a NET_WM_MOVERESIZE message to the x11 window manager, enabling the
// move/resize mode.  As per NET_WM_MOVERESIZE spec, |location| is the position
// in pixels (relative to the root window) of mouse button press, and
// |direction| indicates whether this is a move or resize event, and if it is a
// resize event, which edges of the window the size grip applies to.
COMPONENT_EXPORT(UI_BASE_X)
void DoWMMoveResize(x11::Connection* connection,
                    x11::Window root_window,
                    x11::Window window,
                    const gfx::Point& location_px,
                    int direction);

// Checks if the window manager has set a specific state.
COMPONENT_EXPORT(UI_BASE_X)
bool HasWMSpecProperty(const base::flat_set<x11::Atom>& properties,
                       x11::Atom atom);

// Determine whether we should default to native decorations or the custom
// frame based on the currently-running window manager.
COMPONENT_EXPORT(UI_BASE_X) bool GetCustomFramePrefDefault();

static const int kAllDesktops = -1;
// Queries the desktop |window| is on, kAllDesktops if sticky. Returns false if
// property not found.
COMPONENT_EXPORT(UI_BASE_X)
bool GetWindowDesktop(x11::Window window, int* desktop);

// Translates an X11 error code into a printable string.
COMPONENT_EXPORT(UI_BASE_X)
std::string GetX11ErrorString(XDisplay* display, int err);

// Implementers of this interface receive a notification for every X window of
// the main display.
class EnumerateWindowsDelegate {
 public:
  // |window| is the X Window ID of the enumerated window.  Return true to stop
  // further iteration.
  virtual bool ShouldStopIterating(x11::Window window) = 0;

 protected:
  virtual ~EnumerateWindowsDelegate() = default;
};

// Enumerates all windows in the current display.  Will recurse into child
// windows up to a depth of |max_depth|.
COMPONENT_EXPORT(UI_BASE_X)
bool EnumerateAllWindows(EnumerateWindowsDelegate* delegate, int max_depth);

// Enumerates the top-level windows of the current display.
COMPONENT_EXPORT(UI_BASE_X)
void EnumerateTopLevelWindows(ui::EnumerateWindowsDelegate* delegate);

// Returns all children windows of a given window in top-to-bottom stacking
// order.
COMPONENT_EXPORT(UI_BASE_X)
bool GetXWindowStack(x11::Window window, std::vector<x11::Window>* windows);

enum WindowManagerName {
  WM_OTHER,    // We were able to obtain the WM's name, but there is
               // no corresponding entry in this enum.
  WM_UNNAMED,  // Either there is no WM or there is no way to obtain
               // the WM name.

  WM_AWESOME,
  WM_BLACKBOX,
  WM_COMPIZ,
  WM_ENLIGHTENMENT,
  WM_FLUXBOX,
  WM_I3,
  WM_ICE_WM,
  WM_ION3,
  WM_KWIN,
  WM_MATCHBOX,
  WM_METACITY,
  WM_MUFFIN,
  WM_MUTTER,
  WM_NOTION,
  WM_OPENBOX,
  WM_QTILE,
  WM_RATPOISON,
  WM_STUMPWM,
  WM_WMII,
  WM_XFWM4,
  WM_XMONAD,
};
// Attempts to guess the window maager. Returns WM_OTHER or WM_UNNAMED
// if we can't determine it for one reason or another.
COMPONENT_EXPORT(UI_BASE_X) WindowManagerName GuessWindowManager();

// The same as GuessWindowManager(), but returns the raw string.  If we
// can't determine it, return "Unknown".
COMPONENT_EXPORT(UI_BASE_X) std::string GuessWindowManagerName();

// Returns a buest-effort guess as to whether |window_manager| is tiling (true)
// or stacking (false).
COMPONENT_EXPORT(UI_BASE_X) bool IsWmTiling(WindowManagerName window_manager);

// Returns true if a compositing manager is present.
COMPONENT_EXPORT(UI_BASE_X) bool IsCompositingManagerPresent();

// Enable the default X error handlers. These will log the error and abort
// the process if called. Use SetX11ErrorHandlers() from x11_util_internal.h
// to set your own error handlers.
COMPONENT_EXPORT(UI_BASE_X) void SetDefaultX11ErrorHandlers();

// Returns true if a given window is in full-screen mode.
COMPONENT_EXPORT(UI_BASE_X) bool IsX11WindowFullScreen(x11::Window window);

// Returns true if the window manager supports the given hint.
COMPONENT_EXPORT(UI_BASE_X) bool WmSupportsHint(x11::Atom atom);

// Returns the ICCProfile corresponding to |monitor| using XGetWindowProperty.
COMPONENT_EXPORT(UI_BASE_X)
gfx::ICCProfile GetICCProfileForMonitor(int monitor);

// Return true if the display supports SYNC extension.
COMPONENT_EXPORT(UI_BASE_X) bool IsSyncExtensionAvailable();

// Returns the preferred Skia colortype for an X11 visual.  LOG(FATAL)'s if
// there isn't a suitable colortype.
COMPONENT_EXPORT(UI_BASE_X)
SkColorType ColorTypeForVisual(void* visual);

COMPONENT_EXPORT(UI_BASE_X)
x11::Future<void> SendClientMessage(
    x11::Window window,
    x11::Window target,
    x11::Atom type,
    const std::array<uint32_t, 5> data,
    x11::EventMask event_mask = x11::EventMask::SubstructureNotify |
                                x11::EventMask::SubstructureRedirect);

// Manages a piece of X11 allocated memory as a RefCountedMemory segment. This
// object takes ownership over the passed in memory and will free it with the
// X11 allocator when done.
class COMPONENT_EXPORT(UI_BASE_X) XRefcountedMemory
    : public base::RefCountedMemory {
 public:
  XRefcountedMemory(unsigned char* x11_data, size_t length);

  // Overridden from RefCountedMemory:
  const unsigned char* front() const override;
  size_t size() const override;

 private:
  ~XRefcountedMemory() override;

  gfx::XScopedPtr<unsigned char> x11_data_;
  size_t length_;

  DISALLOW_COPY_AND_ASSIGN(XRefcountedMemory);
};

// Keeps track of a cursor returned by an X function and makes sure it's
// XFreeCursor'd.
class COMPONENT_EXPORT(UI_BASE_X) XScopedCursor {
 public:
  // Keeps track of |cursor| created with |display|.
  XScopedCursor(::Cursor cursor, XDisplay* display);
  ~XScopedCursor();

  ::Cursor get() const;
  void reset(::Cursor cursor);

 private:
  ::Cursor cursor_;
  XDisplay* display_;

  DISALLOW_COPY_AND_ASSIGN(XScopedCursor);
};

struct COMPONENT_EXPORT(UI_BASE_X) XImageDeleter {
  void operator()(XImage* image) const;
};
using XScopedImage = std::unique_ptr<XImage, XImageDeleter>;

namespace test {

// Returns the cached XcursorImage for |cursor|.
COMPONENT_EXPORT(UI_BASE_X)
const XcursorImage* GetCachedXcursorImage(::Cursor cursor);

}  // namespace test

}  // namespace ui

#endif  // UI_BASE_X_X11_UTIL_H_
