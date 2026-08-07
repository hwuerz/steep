CREATE TABLE continuations (
  id VARCHAR,
  submissionId VARCHAR NOT NULL,
  data jsonb NOT NULL,
  results jsonb,
  serial SERIAL NOT NULL,
  PRIMARY KEY(id)
);

CREATE INDEX continuations_submissionId_idx ON continuations (submissionId);
CREATE INDEX continuations_serial_idx ON continuations (serial);
