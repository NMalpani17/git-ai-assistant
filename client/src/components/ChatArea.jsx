import { useEffect, useRef } from "react";
import MessageBubble from "./MessageBubble";
import AssistantResponse from "./AssistantResponse";
import { Loader2, GitBranch } from "lucide-react";

function ChatArea({ messages, isLoading }) {
  const bottomRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isLoading]);

  return (
    <div className="flex-1 overflow-y-auto px-8 py-6 bg-sky-100">
      <div className="max-w-8xl mx-auto space-y-6">
        {messages.length === 0 && (
          <div className="text-center py-16">
            <div className="w-20 h-20 rounded-2xl bg-emerald-500 flex items-center justify-center mx-auto mb-6 shadow-lg">
              <GitBranch className="w-10 h-10 text-white" />
            </div>
            <h2 className="text-4xl font-bold text-slate-800 mb-3">
              Welcome to Git AI Assistant
            </h2>
            <p className="text-xl text-slate-500 mb-8">
              Ask me anything about Git commands in plain English
            </p>
            <div className="flex flex-wrap justify-center gap-3">
              <span className="px-4 py-2 bg-white border border-slate-200 rounded-full text-base text-slate-600 shadow-sm">
                "How do I undo my last commit?"
              </span>
              <span className="px-4 py-2 bg-white border border-slate-200 rounded-full text-base text-slate-600 shadow-sm">
                "Create a new branch"
              </span>
              <span className="px-4 py-2 bg-white border border-slate-200 rounded-full text-base text-slate-600 shadow-sm">
                "What is git rebase?"
              </span>
            </div>
          </div>
        )}

        {messages.map((message) =>
          message.type === "user" ? (
            <MessageBubble key={message.id} content={message.content} />
          ) : (
            <AssistantResponse key={message.id} data={message.data} />
          )
        )}

        {isLoading && (
          <div className="flex items-center gap-3 text-slate-500 text-base">
            <Loader2 className="w-6 h-6 animate-spin text-emerald-500" />
            <span>Thinking...</span>
          </div>
        )}

        <div ref={bottomRef} />
      </div>
    </div>
  );
}

export default ChatArea;
