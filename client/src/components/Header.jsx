import { GitBranch, Server } from "lucide-react";

function Header({ clusterStatus }) {
  const getNodeStatus = (port) => {
    if (!clusterStatus) return "unknown";

    if (clusterStatus.self?.akkaPort === port) {
      return clusterStatus.self?.status === "Up" ? "up" : "down";
    }

    if (clusterStatus.members) {
      const node = clusterStatus.members.find((m) =>
        m.address.includes(String(port))
      );
      if (node) {
        return node.isUp ? "up" : "down";
      }
    }

    return "down";
  };

  const getLeaderPort = () => {
    const leader = clusterStatus?.state?.leader;
    if (!leader || leader === "none") return null;
    if (leader.includes(":2551")) return 2551;
    if (leader.includes(":2552")) return 2552;
    return null;
  };

  const node1Status = getNodeStatus(2551);
  const node2Status = getNodeStatus(2552);
  const leaderPort = getLeaderPort();

  return (
    <header className="bg-white border-b border-slate-200 px-8 py-5 shadow-sm">
      <div className="max-w-8xl mx-auto flex items-center justify-between">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-emerald-500 flex items-center justify-center shadow-md">
            <GitBranch className="w-7 h-7 text-white" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-slate-800">
              Git AI Assistant
            </h1>
            <p className="text-base text-slate-500">
              Natural language to Git commands
            </p>
          </div>
        </div>

        <div className="flex items-center gap-6">
          <div className="flex items-center gap-2 bg-slate-50 px-4 py-2 rounded-lg">
            <Server className="w-5 h-5 text-slate-400" />
            <span className="text-base font-medium text-slate-600">Node 1</span>
            <div
              className={`w-3 h-3 rounded-full ${
                node1Status === "up" ? "bg-emerald-500" : "bg-red-400"
              }`}
            />
            {leaderPort === 2551 && (
              <span className="text-sm font-semibold text-amber-600 bg-amber-50 px-2 py-0.5 rounded">
                Leader
              </span>
            )}
          </div>

          <div className="flex items-center gap-2 bg-slate-50 px-4 py-2 rounded-lg">
            <Server className="w-5 h-5 text-slate-400" />
            <span className="text-base font-medium text-slate-600">Node 2</span>
            <div
              className={`w-3 h-3 rounded-full ${
                node2Status === "up" ? "bg-emerald-500" : "bg-red-400"
              }`}
            />
            {leaderPort === 2552 && (
              <span className="text-sm font-semibold text-amber-600 bg-amber-50 px-2 py-0.5 rounded">
                Leader
              </span>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}

export default Header;
