CREATE TABLE repositories (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  local_path VARCHAR(1024) NOT NULL,
  project_type VARCHAR(100) NOT NULL DEFAULT 'UNKNOWN',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_repositories_local_path (local_path(500))
);
CREATE TABLE scans (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  repository_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  summary TEXT NULL,
  run_build BOOLEAN NOT NULL DEFAULT FALSE,
  run_lint BOOLEAN NOT NULL DEFAULT FALSE,
  started_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_scans_repository FOREIGN KEY (repository_id) REFERENCES repositories(id),
  INDEX idx_scans_repository_created (repository_id, created_at)
);
CREATE TABLE diagnostics (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  scan_id BIGINT NOT NULL,
  error_type VARCHAR(100) NOT NULL,
  severity VARCHAR(30) NOT NULL DEFAULT 'ERROR',
  file_path VARCHAR(1024) NULL,
  line_number INT NULL,
  message TEXT NOT NULL,
  evidence TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_diagnostics_scan FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE,
  INDEX idx_diagnostics_scan (scan_id)
);
CREATE TABLE repairs (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  scan_id BIGINT NOT NULL,
  diagnostic_id BIGINT NULL,
  status VARCHAR(40) NOT NULL,
  explanation TEXT NULL,
  patch_json MEDIUMTEXT NULL,
  model_name VARCHAR(150) NULL,
  validation_summary TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_repairs_scan FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE,
  CONSTRAINT fk_repairs_diagnostic FOREIGN KEY (diagnostic_id) REFERENCES diagnostics(id) ON DELETE SET NULL,
  INDEX idx_repairs_scan (scan_id)
);
CREATE TABLE validation_results (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  repair_id BIGINT NOT NULL,
  command_name VARCHAR(120) NOT NULL,
  status VARCHAR(32) NOT NULL,
  exit_code INT NULL,
  output_summary MEDIUMTEXT NULL,
  executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_validation_repair FOREIGN KEY (repair_id) REFERENCES repairs(id) ON DELETE CASCADE,
  INDEX idx_validation_repair (repair_id)
);
CREATE TABLE model_runs (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  scan_id BIGINT NULL,
  repair_id BIGINT NULL,
  model_name VARCHAR(150) NOT NULL,
  operation VARCHAR(80) NOT NULL,
  status VARCHAR(32) NOT NULL,
  duration_ms BIGINT NULL,
  error_summary TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_model_runs_scan FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE SET NULL,
  CONSTRAINT fk_model_runs_repair FOREIGN KEY (repair_id) REFERENCES repairs(id) ON DELETE SET NULL,
  INDEX idx_model_runs_created (created_at)
);
