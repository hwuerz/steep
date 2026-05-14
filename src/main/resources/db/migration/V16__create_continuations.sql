CREATE TABLE continuations (
  id VARCHAR,
  suspendActionId VARCHAR NOT NULL,
  submissionId VARCHAR NOT NULL,
  status VARCHAR NOT NULL,
  data jsonb NOT NULL,
  results jsonb,
  serial SERIAL NOT NULL,
  PRIMARY KEY(id)
);

CREATE INDEX continuations_submissionId_idx ON continuations (submissionId);
CREATE INDEX continuations_status_idx ON continuations (status);
CREATE INDEX continuations_serial_idx ON continuations (serial);
