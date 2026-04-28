import { useMemo, useState } from "react";
import { uploadFile } from "../api";
import { addRecentFile } from "../utils/recentFiles";

export default function UploadPage() {
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [success, setSuccess] = useState("");
  const [error, setError] = useState("");

  const selectedFileText = useMemo(() => {
    if (!selectedFile) return "No file selected";
    return `${selectedFile.name} (${Math.ceil(selectedFile.size / 1024)} KB)`;
  }, [selectedFile]);

  async function handleSubmit(event) {
    event.preventDefault();
    if (!selectedFile) {
      setError("Please choose a file first.");
      setSuccess("");
      return;
    }

    setUploading(true);
    setError("");
    setSuccess("");

    try {
      const result = await uploadFile(selectedFile);
      const entry = {
        fileId: result.fileId,
        filename: selectedFile.name,
        uploadedAt: new Date().toISOString()
      };
      addRecentFile(entry);
      setSuccess(`Upload successful. File ID: ${result.fileId}`);
      setSelectedFile(null);
    } catch (err) {
      setError(err.message || "Upload failed.");
    } finally {
      setUploading(false);
    }
  }

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
          onChange={(event) => setSelectedFile(event.target.files?.[0] || null)}
          disabled={uploading}
        />
        <p className="field-note">{selectedFileText}</p>

        <button className="primary-button" type="submit" disabled={uploading}>
          {uploading ? "Uploading..." : "Upload File"}
        </button>
      </form>

      {success ? <p className="message success">{success}</p> : null}
      {error ? <p className="message error">{error}</p> : null}
    </section>
  );
}
