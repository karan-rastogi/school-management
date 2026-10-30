import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { landingPathFor } from "../auth/redirect";
import { fetchAuthConfig, googleSignInUrl, requestOtp, toApiError } from "../lib/api";

const SIGN_IN_ERRORS: Record<string, string> = {
  not_registered: "That Google account is not registered with the school. Ask the school office to add it.",
  account_disabled: "That account has been deactivated. Contact the school office.",
  no_email: "Google did not share an email address, so we could not match your account.",
  oauth_failed: "We could not complete the Google sign-in. Please try again.",
};

export function LoginPage() {
  const { status, user, signInWithPassword, signInWithOtp } = useAuth();
  const [searchParams] = useSearchParams();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [codeRequested, setCodeRequested] = useState(false);

  const configQuery = useQuery({ queryKey: ["authConfig"], queryFn: fetchAuthConfig });

  const passwordLogin = useMutation({
    mutationFn: () => signInWithPassword(email.trim(), password),
  });

  const requestCode = useMutation({
    mutationFn: () => requestOtp(phone.trim()),
    onSuccess: () => setCodeRequested(true),
  });

  const verifyCode = useMutation({
    mutationFn: () => signInWithOtp(phone.trim(), code.trim()),
  });

  if (status === "authenticated") {
    return <Navigate to={landingPathFor(user)} replace />;
  }

  if (configQuery.isPending) {
    return <p className="mx-auto max-w-md py-10 text-sm text-slate-500">Loading sign-in options…</p>;
  }

  if (configQuery.isError) {
    return (
      <div className="mx-auto max-w-md space-y-3 py-10">
        <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {toApiError(configQuery.error).message}
        </p>
        <button
          type="button"
          onClick={() => void configQuery.refetch()}
          className="rounded-md border border-slate-300 px-3 py-1 text-sm font-medium hover:bg-slate-100"
        >
          Retry
        </button>
      </div>
    );
  }

  const config = configQuery.data;
  const showPassword = config.passwordLoginEnabled;
  const showGoogle = config.oauthEnabled;
  const showOtp = config.otpEnabled;
  const methodCount = [showPassword, showGoogle, showOtp].filter(Boolean).length;

  const redirectError = searchParams.get("error");
  const errorMessage = passwordLogin.error
    ? toApiError(passwordLogin.error).message
    : verifyCode.error
      ? toApiError(verifyCode.error).message
      : requestCode.error
        ? toApiError(requestCode.error).message
        : redirectError
          ? (SIGN_IN_ERRORS[redirectError] ?? "Sign-in failed. Please try again.")
          : null;

  return (
    <div className="mx-auto max-w-md space-y-6 py-10">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold">Sign in</h1>
        <p className="text-sm text-slate-600">
          Accounts are created by the school office. There is no self sign-up.
        </p>
      </header>

      {errorMessage ? (
        <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {errorMessage}
        </p>
      ) : null}

      {showPassword ? (
        <form
          className="space-y-3"
          onSubmit={(event) => {
            event.preventDefault();
            passwordLogin.mutate();
          }}
        >
          <label className="block space-y-1">
            <span className="text-sm font-medium text-slate-700">Email</span>
            <input
              type="email"
              required
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@school.test"
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </label>

          <label className="block space-y-1">
            <span className="text-sm font-medium text-slate-700">Password</span>
            <input
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </label>

          <button
            type="submit"
            disabled={passwordLogin.isPending}
            className="w-full rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500 disabled:opacity-60"
          >
            Sign in
          </button>
        </form>
      ) : null}

      {showPassword && methodCount > 1 ? <Separator /> : null}

      {showGoogle ? (
        <a
          href={googleSignInUrl()}
          className="flex w-full items-center justify-center gap-2 rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium hover:bg-slate-50"
        >
          Continue with Google
        </a>
      ) : null}

      {showGoogle && showOtp ? <Separator /> : null}

      {showOtp ? (
        <form
          className="space-y-3"
          onSubmit={(event) => {
            event.preventDefault();
            if (codeRequested) {
              verifyCode.mutate();
            } else {
              requestCode.mutate();
            }
          }}
        >
          <label className="block space-y-1">
            <span className="text-sm font-medium text-slate-700">Phone number</span>
            <input
              type="tel"
              required
              value={phone}
              onChange={(event) => {
                setPhone(event.target.value);
                setCodeRequested(false);
              }}
              placeholder="+91 98765 43210"
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </label>

          {codeRequested ? (
            <label className="block space-y-1">
              <span className="text-sm font-medium text-slate-700">Verification code</span>
              <input
                inputMode="numeric"
                required
                value={code}
                onChange={(event) => setCode(event.target.value)}
                placeholder="123456"
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm tracking-widest"
              />
            </label>
          ) : null}

          <button
            type="submit"
            disabled={requestCode.isPending || verifyCode.isPending}
            className="w-full rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500 disabled:opacity-60"
          >
            {codeRequested ? "Verify and sign in" : "Login with phone OTP"}
          </button>
        </form>
      ) : null}

      {showOtp && codeRequested ? (
        <div className="space-y-1 text-sm text-slate-600">
          <p>We sent a 6-digit code to {phone.trim()}. It expires in 5 minutes.</p>
          <button
            type="button"
            className="text-indigo-600 hover:underline"
            onClick={() => {
              setCodeRequested(false);
              setCode("");
            }}
          >
            Use a different number
          </button>
        </div>
      ) : null}
    </div>
  );
}

function Separator() {
  return (
    <div className="flex items-center gap-3 text-xs uppercase tracking-wide text-slate-400">
      <span className="h-px flex-1 bg-slate-200" />
      or
      <span className="h-px flex-1 bg-slate-200" />
    </div>
  );
}
