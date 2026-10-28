export interface ApiProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  timestamp?: string;
  errors?: FieldViolation[];
}

export interface FieldViolation {
  field: string;
  message: string;
}
