export const ENVIRONMENTS = [
  { id: "dev", label: "Development", issuer: "https://dev-issuer.example.com" },
  { id: "test", label: "Staging", issuer: "https://staging-issuer.example.com" },
  { id: "vendor", label: "Production", issuer: "https://prod-issuer.example.com" }
];

export const ENVIRONMENT_IDS = new Set(ENVIRONMENTS.map((env) => env.id));