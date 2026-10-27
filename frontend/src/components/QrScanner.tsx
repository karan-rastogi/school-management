import jsQR from "jsqr";
import { useEffect, useRef, useState } from "react";

interface QrScannerProps {
  active: boolean;
  onDecode: (token: string) => void;
}

export function QrScanner({ active, onDecode }: QrScannerProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const decodeRef = useRef(onDecode);
  const lastRef = useRef<{ token: string; at: number } | null>(null);
  const [error, setError] = useState<string | null>(null);

  decodeRef.current = onDecode;

  useEffect(() => {
    if (!active) {
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      setError("This browser cannot open the camera. Use manual entry below.");
      return;
    }

    let stream: MediaStream | null = null;
    let frame = 0;
    let stopped = false;

    const tick = () => {
      if (stopped) {
        return;
      }
      const video = videoRef.current;
      const canvas = canvasRef.current;
      if (video && canvas && video.readyState === video.HAVE_ENOUGH_DATA && video.videoWidth > 0) {
        canvas.width = video.videoWidth;
        canvas.height = video.videoHeight;
        const context = canvas.getContext("2d");
        if (context) {
          context.drawImage(video, 0, 0, canvas.width, canvas.height);
          const image = context.getImageData(0, 0, canvas.width, canvas.height);
          const code = jsQR(image.data, image.width, image.height);
          if (code?.data) {
            const now = Date.now();
            const last = lastRef.current;
            if (!last || last.token !== code.data || now - last.at > 4000) {
              lastRef.current = { token: code.data, at: now };
              decodeRef.current(code.data);
            }
          }
        }
      }
      frame = window.requestAnimationFrame(tick);
    };

    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: "environment" } })
      .then((media) => {
        stream = media;
        const video = videoRef.current;
        if (!video) {
          return;
        }
        video.srcObject = media;
        void video.play();
        setError(null);
        frame = window.requestAnimationFrame(tick);
      })
      .catch(() => setError("Camera permission denied. Use manual entry below."));

    return () => {
      stopped = true;
      window.cancelAnimationFrame(frame);
      stream?.getTracks().forEach((track) => track.stop());
    };
  }, [active]);

  return (
    <div className="space-y-2">
      <video
        ref={videoRef}
        playsInline
        muted
        className="h-56 w-full rounded-lg bg-slate-900 object-cover"
      />
      <canvas ref={canvasRef} className="hidden" />
      {error ? <p className="text-xs text-amber-700">{error}</p> : null}
    </div>
  );
}
