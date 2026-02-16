import { useState } from "react";
import { Send } from "lucide-react";
import QuickButtons from "./QuickButtons";

function InputArea({ onSend, isLoading }) {
  const [input, setInput] = useState("");

  const handleSubmit = (e) => {
    e.preventDefault();
    if (input.trim() && !isLoading) {
      onSend(input.trim());
      setInput("");
    }
  };

  return (
    <div className="border-t border-slate-200 bg-white px-8 py-5 shadow-lg">
      <div className="max-w-full mx-auto">
        <QuickButtons
          onSelect={(q) => !isLoading && onSend(q)}
          disabled={isLoading}
        />
        <form onSubmit={handleSubmit} className="flex gap-4">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask about any Git command..."
            disabled={isLoading}
            className="flex-1 bg-white border border-slate-200 rounded-xl px-5 py-4 text-lg text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent transition-all disabled:opacity-50"
          />
          <button
            type="submit"
            disabled={isLoading || !input.trim()}
            className="bg-emerald-500 hover:bg-emerald-600 disabled:bg-slate-300 disabled:cursor-not-allowed text-white font-semibold px-8 py-4 rounded-xl flex items-center gap-2 transition-colors shadow-md"
          >
            <Send className="w-5 h-5" />
            <span className="text-base">Send</span>
          </button>
        </form>
      </div>
    </div>
  );
}

export default InputArea;
