import {
  Bot,
  Copy,
  Check,
  AlertTriangle,
  Lightbulb,
  Clock,
  Server,
  Info,
} from "lucide-react";
import { useState } from "react";

function AssistantResponse({ data }) {
  const [copied, setCopied] = useState(false);

  const copyCommand = () => {
    if (data.command) {
      navigator.clipboard.writeText(data.command);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const getSafetyConfig = (level) => {
    switch (level) {
      case "SAFE":
        return {
          badge: "bg-emerald-100 text-emerald-700",
          border: "border-emerald-200",
          bg: "bg-emerald-50",
          text: "text-emerald-700",
        };
      case "CAUTION":
        return {
          badge: "bg-amber-100 text-amber-700",
          border: "border-amber-200",
          bg: "bg-amber-50",
          text: "text-amber-700",
        };
      case "DANGEROUS":
        return {
          badge: "bg-red-100 text-red-700",
          border: "border-red-200",
          bg: "bg-red-50",
          text: "text-red-700",
        };
      default:
        return {
          badge: "bg-slate-100 text-slate-700",
          border: "border-slate-200",
          bg: "bg-slate-50",
          text: "text-slate-700",
        };
    }
  };

  if (!data.success) {
    return (
      <div className="flex justify-start">
        <div className="flex items-start gap-3 max-w-[75%]">
          <div className="w-10 h-10 rounded-full bg-slate-100 flex items-center justify-center flex-shrink-0">
            <Bot className="w-5 h-5 text-slate-600" />
          </div>
          <div className="bg-white border border-slate-200 rounded-2xl rounded-tl-md px-5 py-4 shadow-md">
            <div className="flex items-center gap-2 mb-2">
              <Info className="w-5 h-5 text-blue-500" />
              <span className="text-sm font-medium text-blue-600">Info</span>
            </div>
            <p className="text-lg text-slate-700">
              {data.error || "Something went wrong"}
            </p>
          </div>
        </div>
      </div>
    );
  }

  const config = getSafetyConfig(data.safetyLevel);

  return (
    <div className="flex justify-start">
      <div className="flex items-start gap-3 max-w-[75%]">
        <div className="w-10 h-10 rounded-full bg-emerald-100 flex items-center justify-center flex-shrink-0">
          <Bot className="w-5 h-5 text-emerald-600" />
        </div>

        <div
          className={`bg-white border ${config.border} rounded-2xl rounded-tl-md overflow-hidden shadow-sm`}
        >
          <div className="p-5">
            <div className="flex items-center justify-between mb-3">
              <span
                className={`px-3 py-1 rounded-full text-sm font-semibold ${config.badge}`}
              >
                {data.safetyLevel || "UNKNOWN"}
              </span>
              <button
                onClick={copyCommand}
                className="flex items-center gap-1.5 text-slate-400 hover:text-slate-600 transition-colors"
              >
                {copied ? (
                  <>
                    <Check className="w-4 h-4 text-emerald-500" />
                    <span className="text-sm text-emerald-500">Copied!</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-4 h-4" />
                    <span className="text-sm">Copy</span>
                  </>
                )}
              </button>
            </div>
            <div className="bg-slate-800 rounded-xl p-4 font-mono text-lg text-emerald-400">
              {data.command || "No command generated"}
            </div>
          </div>

          {data.explanation && (
            <div className="px-5 pb-4">
              <p className="text-lg text-slate-600 leading-relaxed">
                {data.explanation}
              </p>
            </div>
          )}

          {data.warnings && data.warnings.length > 0 && (
            <div className={`px-5 py-4 ${config.bg} border-t ${config.border}`}>
              <div className="flex items-center gap-2 mb-2">
                <AlertTriangle className={`w-5 h-5 ${config.text}`} />
                <span className={`font-semibold text-sm ${config.text}`}>
                  Warnings
                </span>
              </div>
              <ul className="space-y-1">
                {data.warnings.map((w, i) => (
                  <li key={i} className="text-base text-slate-600">
                    • {w}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {data.alternatives && data.alternatives.length > 0 && (
            <div className="px-5 py-4 bg-blue-50 border-t border-blue-100">
              <div className="flex items-center gap-2 mb-2">
                <Lightbulb className="w-5 h-5 text-blue-600" />
                <span className="text-blue-700 font-semibold text-sm">
                  Safer Alternatives
                </span>
              </div>
              <ul className="space-y-1">
                {data.alternatives.map((a, i) => (
                  <li key={i} className="text-base text-slate-600">
                    • {a}
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div className="px-5 py-3 bg-slate-50 border-t border-slate-100 flex items-center gap-4 text-sm text-slate-400">
            {data.responseTimeMs && (
              <div className="flex items-center gap-1.5">
                <Clock className="w-4 h-4" />
                <span>{data.responseTimeMs}ms</span>
              </div>
            )}
            {data.nodePort && (
              <div className="flex items-center gap-1.5">
                <Server className="w-4 h-4" />
                <span>{data.nodePort === 8080 ? "Node 1" : "Node 2"}</span>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default AssistantResponse;
