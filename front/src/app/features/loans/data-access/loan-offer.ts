/**
 * Preview of the offer the backend computes on approval (LoanOffer.calculate): effective annual rate converted to
 * monthly, fixed installment by French amortization, rounded to cents. The server's figures are authoritative;
 * this only lets the admin see the result before confirming.
 */
export interface LoanOfferPreview {
  monthlyPayment: number;
  totalPayable: number;
  totalInterest: number;
}

export function monthlyRateFromEffectiveAnnual(annualRatePercent: number): number {
  return Math.pow(1 + annualRatePercent / 100, 1 / 12) - 1;
}

export function calculateOffer(principal: number, months: number, annualRatePercent: number): LoanOfferPreview {
  const monthlyRate = monthlyRateFromEffectiveAnnual(annualRatePercent);
  const payment =
    monthlyRate === 0 ? principal / months : (principal * monthlyRate) / (1 - Math.pow(1 + monthlyRate, -months));
  const monthlyPayment = roundCents(payment);
  const totalPayable = roundCents(monthlyPayment * months);
  return { monthlyPayment, totalPayable, totalInterest: Math.max(0, roundCents(totalPayable - principal)) };
}

function roundCents(value: number): number {
  return Math.round(value * 100) / 100;
}
