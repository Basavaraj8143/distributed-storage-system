import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { uploadFile } from "../api";
import { addRecentFile } from "../utils/recentFiles";

// Keep in sync with spring.servlet.multipart.max-file-size on the master.
const MAX_UPLOAD_MB = Number(import.meta.env.VITE_MAX_UPLOAD_MB || 10);
const MAX_UPLOAD_BYTES = MAX_UPLOAD_MB * 1024 * 1024;

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

// Same rules the server enforces, so the user gets feedback before any bytes are sent.
function validateFile(file) {
  if (!file) return "Please choose a file first.";
  if (file.size === 0) return "This file is empty. Choose a file that has content.";
  if (file.size > MAX_UPLOAD_BYTES) {
    return `This file is ${formatSize(file.size)}. The limit is ${MAX_UPLOAD_MB} MB.`;
  }
  return "";
}

export default function UploadPage() {
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [uploadedId, setUploadedId] = useState("");
  const [uploadedName, setUploadedName] = useState("");
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState("");

  const selectedFileText = useMemo(() => {
    if (!selectedFile) return `No file selected (max ${MAX_UPLOAD_MB} MB)`;
    return `${selectedFile.name} (${formatSize(selectedFile.size)})`;
  }, [selectedFile]);

  function handleFileChange(event) {
    const file = event.target.files?.[0] || null;
    setSelectedFile(file);
    setUploadedId("");
    setCopied(false);
    // Show a size/empty problem straight away instead of after the click.
    setError(file ? validateFile(file) : "");
  }

  async function handleSubmit(event) {
    event.preventDefault();

    const problem = validateFile(selectedFile);
    if (problem) {
      setError(problem);
      return;
    }

    setUploading(true);
    setProgress(0);
    setError("");
    setUploadedId("");
    setCopied(false);

    try {
      const result = await uploadFile(selectedFile, setProgress);
      addRecentFile({
        fileId: result.fileId,
        filename: selectedFile.name,
        uploadedAt: new Date().toISOString()
      });
      setUploadedId(result.fileId);
      setUploadedName(selectedFile.name);
      setSelectedFile(null);
      // Clear the native input too, otherwise it still shows the old file name.
      event.target.reset();
    } catch (err) {
      setError(err.message || "Upload failed.");
    } finally {
      setUploading(false);
    }
  }

  async function copyId() {
    try {
      await navigator.clipboard.writeText(uploadedId);
      setCopied(true);
    } catch {
      // Clipboard can be blocked (non-HTTPS, permissions); the ID is still visible to copy by hand.
      setCopied(false);
    }
  }

  const blocked = Boolean(selectedFile && validateFile(selectedFile));

  return (
    <section className="page">
      <p className="eyebrow">Core Flow</p>
      <h2>Upload</h2>
      <p className="page-copy">Upload a file and keep the returned file ID for download.</p>

      <form className="action-panel" onSubmit={handleSubmit}>
        <label className="field-label" htmlFor="upload-file-input">
          Select file
        </label>
        <input
          id="upload-file-input"
          className="text-input"
          type="file"
          onChange={handleFileChange}
          disabled={uploading}
        />
        <p className="field-note">{selectedFileText}</p>

        {uploading ? (
          <div className="progress-block" role="status" aria-live="polite">
            <progress className="progress-bar" value={progress} max="100" />
            <p className="field-note">
              {progress < 100
                ? `Sending to the master... ${progress}%`
                : "Sent. The master is splitting the file and replicating chunks..."}
            </p>
          </div>
        ) : null}

        <button className="primary-button" type="submit" disabled={uploading || blocked}>
          {uploading ? "Uploading..." : "Upload File"}
        </button>
      </form>

      {uploadedId ? (
        <div className="message success">
          <p className="success-line">
            <strong>{uploadedName}</strong> uploaded.
          </p>
          <p className="success-line">
            File ID: <span className="mono-cell">{uploadedId}</span>
          </p>
          <div className="row-actions">
            <button className="ghost-button small" type="button" onClick={copyId}>
              {copied ? "Copied" : "Copy ID"}
            </button>
            <Link className="ghost-button small link-button" to="/files">
              View in Files
            </Link>
          </div>
        </div>
      ) : null}
      {error ? <p className="message error">{error}</p> : null}
    </section>
  );
}
