import { parseServerSentEvent } from './loan-events.service';

describe('parseServerSentEvent', () => {
  it('reads the event name and data sent by Spring', () => {
    const block = 'id:1\nevent:loan-status\ndata:{"loanId":"1","status":"APPROVED"}';

    expect(parseServerSentEvent(block)).toEqual({ event: 'loan-status', data: '{"loanId":"1","status":"APPROVED"}' });
  });

  it('accepts a space after the colon and joins multi-line data', () => {
    expect(parseServerSentEvent('event: note\ndata: first\ndata: second')).toEqual({
      event: 'note',
      data: 'first\nsecond',
    });
  });

  it('ignores heartbeat comments', () => {
    expect(parseServerSentEvent(':keep-alive')).toBeNull();
  });
});
