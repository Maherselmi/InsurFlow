export interface ClaimDocument {
  id: number;
  fileName: string;
  fileType: string;
  filePath: string;
}

export interface Claim {
  id: number;
  description: string;
  incidentDate: string;
  status: string;
  createdAt: string;
  aiReport?: string;
  clientReport?: string;
  policy?: {
    id: number;
    policyNumber: string;
    type?: string;
    client?: {
      id: number;
      firstName: string;
      lastName: string;
      email?: string;
      phone?: string;
    };
  };
  documents?: ClaimDocument[];
}
