export interface AgentResult {
  id: number;
  agentName: string;
  conclusion: string;
  confidenceScore: number;
  rawLlmResponse?: string;
  needsHumanReview?: boolean;
  createdAt?: string;
}
