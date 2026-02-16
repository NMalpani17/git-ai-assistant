const quickQueries = [
  { label: "Undo last commit", query: "How do I undo my last commit?" },
  { label: "Create branch", query: "How to create a new branch?" },
  { label: "Merge branches", query: "How to merge branches?" },
  { label: "Discard changes", query: "How to discard all local changes?" },
  { label: "Force push safely", query: "How to force push safely?" },
];

function QuickButtons({ onSelect, disabled }) {
  return (
    <div className="flex flex-wrap gap-2 mb-4">
      {quickQueries.map((item, index) => (
        <button
          key={index}
          onClick={() => onSelect(item.query)}
          disabled={disabled}
          className="px-4 py-2 text-sm font-medium bg-slate-100 hover:bg-emerald-50 hover:text-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed text-slate-600 border border-slate-200 rounded-full transition-all"
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}

export default QuickButtons;
