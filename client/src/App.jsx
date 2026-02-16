import { useState, useEffect } from "react";
import Header from "./components/Header";
import ChatArea from "./components/ChatArea";
import InputArea from "./components/InputArea";

function App() {
  const [messages, setMessages] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [clusterStatus, setClusterStatus] = useState(null);

  const fetchWithFallback = async (path) => {
    try {
      const response = await fetch(`http://localhost:8080${path}`);
      if (!response.ok) throw new Error("Node 1 failed");
      return response;
    } catch {
      console.log("Node 1 unavailable, trying Node 2...");
      return fetch(`http://localhost:8081${path}`);
    }
  };

  const sendMessage = async (query) => {
    const userMessage = {
      id: Date.now(),
      type: "user",
      content: query,
    };
    setMessages((prev) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const response = await fetchWithFallback(
        `/api/git/ask?q=${encodeURIComponent(query)}`
      );
      const data = await response.json();

      const assistantMessage = {
        id: Date.now() + 1,
        type: "assistant",
        data: data,
      };
      setMessages((prev) => [...prev, assistantMessage]);
    } catch {
      const errorMessage = {
        id: Date.now() + 1,
        type: "assistant",
        data: {
          success: false,
          error:
            "Both nodes are unavailable. Please check if backend is running.",
        },
      };
      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const fetchClusterStatus = async () => {
    try {
      const response = await fetchWithFallback("/api/cluster/status");
      const data = await response.json();
      setClusterStatus(data);
    } catch (error) {
      console.error("Failed to fetch cluster status:", error);
    }
  };

  // Fetch cluster status on mount
  useEffect(() => {
    fetchClusterStatus();
    const interval = setInterval(fetchClusterStatus, 10000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="flex flex-col h-screen bg-sky-100">
      <Header clusterStatus={clusterStatus} />
      <ChatArea messages={messages} isLoading={isLoading} />
      <InputArea onSend={sendMessage} isLoading={isLoading} />
    </div>
  );
}

export default App;
