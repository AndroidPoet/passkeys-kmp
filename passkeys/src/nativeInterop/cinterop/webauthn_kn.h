/*
 * Umbrella header for the Kotlin/Native cinterop binding to the Windows
 * WebAuthn API. webauthn.h relies on Win32 types (HWND, DWORD, PCWSTR, GUID,
 * HRESULT, ...) from <windows.h>, so pull that in first. headerFilter in the
 * .def keeps only this header's and webauthn.h's declarations on the Kotlin API
 * surface (all of windows.h would be far too much).
 *
 * WebAuthNAuthenticatorMakeCredential/GetAssertion need a parent HWND. The two
 * window-handle helpers below live in winuser.h / wincon.h (pulled in by
 * windows.h) but are header-filtered out, so they are re-declared here — with
 * signatures identical to the SDK's — purely so cinterop generates Kotlin
 * bindings for them. Used to resolve a default anchor when the caller does not
 * supply a window handle.
 */
#include <windows.h>
#include "webauthn.h"

HWND WINAPI GetForegroundWindow(void);
HWND WINAPI GetConsoleWindow(void);
