import { calculateOffer, monthlyRateFromEffectiveAnnual } from './loan-offer';

describe('calculateOffer', () => {
  it('converts an effective annual rate to its monthly equivalent', () => {
    expect(monthlyRateFromEffectiveAnnual(26.8242)).toBeCloseTo(0.02, 6);
  });

  it('matches the textbook French amortization installment', () => {
    const offer = calculateOffer(1_000_000, 12, 26.82);

    expect(Math.abs(offer.monthlyPayment - 94_559.6)).toBeLessThan(5);
    expect(offer.totalPayable).toBeCloseTo(offer.monthlyPayment * 12, 2);
    expect(offer.totalInterest).toBeCloseTo(offer.totalPayable - 1_000_000, 2);
  });

  it('splits the principal evenly at 0 %', () => {
    expect(calculateOffer(1_200_000, 12, 0)).toEqual({
      monthlyPayment: 100_000,
      totalPayable: 1_200_000,
      totalInterest: 0,
    });
  });
});
