import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { landingPathFor } from "../auth/redirect";
import { toApiError } from "../lib/api";

export function ChangePasswordPage() {
  const { user, changePassword, signOut } = useAuth();
  const navigate = useNavigate();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const change = useMutation({
    mutationFn: () => changePassword(currentPassword, newPassword),
    onSuccess: (updated) => {
      navigate(landingPathFor(updated), { replace: true });
    },
  });

  const mismatch = confirmPassword.length > 0 && newPassword !== confirmPassword;

  async function handleSignOut() {
    await signOut();
    navigate("/login", { replace: true });
  }

  return (
    <div className="mx-auto max-w-md space-y-6 py-10">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold">Change password</h1>
        <p className="text-sm text-slate-600">
          You must set a new password before continuing{user ? `, ${user.name}` : ""}.
        </p>
      </header>

      {change.error ? (
        <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {toApiError(change.error).message}
        </p>
      ) : null}

      <form
        className="space-y-3"
        onSubmit={(event) => {
          event.preventDefault();
          if (!mismatch) {
            change.mutate();
          }
        }}
      >
        <label className="block space-y-1">
          <span className="text-sm font-medium text-slate-700">Current password</span>
          <input
            type="password"
            required
            autoComplete="current-password"
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </label>

        <label className="block space-y-1">
          <span className="text-sm font-medium text-slate-700">New password</span>
          <input
            type="password"
            required
            minLength={8}
            autoComplete="new-password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
          <span className="text-xs text-slate-500">At least 8 characters.</span>
        </label>

        <label className="block space-y-1">
          <span className="text-sm font-medium text-slate-700">Confirm new password</span>
          <input
            type="password"
            required
            minLength={8}
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
          {mismatch ? <span className="text-xs text-red-600">Passwords do not match.</span> : null}
        </label>

        <button
          type="submit"
          disabled={change.isPending || mismatch}
          className="w-full rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500 disabled:opacity-60"
        >
          Set new password
        </button>
      </form>

      <button type="button" className="text-sm text-indigo-600 hover:underline" onClick={() => void handleSignOut()}>
        Sign out
      </button>
    </div>
  );
}
