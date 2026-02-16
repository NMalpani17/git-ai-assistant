import { User } from "lucide-react";

function MessageBubble({ content }) {
  return (
    <div className="flex justify-end">
      <div className="flex items-start gap-3 max-w-[70%]">
        <div className="bg-emerald-500 text-white rounded-2xl rounded-tr-md px-5 py-3 shadow-sm">
          <p className="text-lg">{content}</p>
        </div>
        <div className="w-10 h-10 rounded-full bg-emerald-100 flex items-center justify-center flex-shrink-0">
          <User className="w-5 h-5 text-emerald-600" />
        </div>
      </div>
    </div>
  );
}

export default MessageBubble;
