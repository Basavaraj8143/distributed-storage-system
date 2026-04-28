import { useMemo, useState } from "react";
import { downloadFile } from "../api";
import { getRecentFiles } from "../utils/recentFiles";

function formatTime(value) {
  try {
    return new Date(value).toLocaleString();
  } catch {
    return "-";
  }
}

function parseFilenameFromContentDisposition(contentDisposition) {
  if (!contentDisposition) return "";
  const match = contentDisposition.match(/filename\*?=(?:UTF-8''|")?([^\";]+)/i);
  if (!match?.[1]) return "";
  return decodeURIComponent(match[1].replace(/"/g, "").trim());
}

function extensionFromContentType(contentType) {
  const map = {
    "application/pdf": ".pdf",
    "image/png": ".png",
    "image/jpeg": ".jpg",
    "text/plain": ".txt",
    "application/json": ".json",
    "application/zip": ".zip"
  };
  return map[(contentType || "").toLowerCase()] || "";
}

export default function FilesPage() {
  const [fileIdInput, setFileIdInput] = useState("");
  const [downloadingId, setDownloadingId] = useState("");
  const [success, setSuccess] = useState("");
  const [error, setError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);

  const recentFiles = useMemo(() => getRecentFiles(), [refreshKey]);

  async function startDownload(fileId, filenameHint) {
    if (!fileId) {
      setError("fileId is required.");
      setSuccess("");
      return;
    }

    setDownloadingId(fileId);
    setError("");
    setSuccess("");

    try {
      const result = await downloadFile(fileId);
      const blob = result.blob;
      const downloadUrl = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = downloadUrl;
      const serverFilename = parseFilenameFromContentDisposition(result.contentDisposition);
      const fallbackName = `download-${fileId}${extensionFromContentType(result.contentType)}`;
      link.download = filenameHint || serverFilename || fallbackName;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(downloadUrl);
      setSuccess(`Download started for ${fileId}`);
    } catch (err) {
      setError(err.message || "Download failed.");
    } finally {
      setDownloadingId("");
    }
  }

  return (
    <section className="page">
      <p className="eyebrow">Core Flow</p>
      <h2>Files</h2>
      <p className="page-copy">Download by file ID or use a recent uploaded entry.</p>

      <form
        className="action-panel inline-form"
        onSubmit={(event) => {
          event.preventDefault();
          startDownload(fileIdInput.trim());
        }}
      >
        <label className="field-label" htmlFor="file-id-input">
          File ID
        </label>
        <input
          id="file-id-input"
          className="text-input"
          type="text"
          placeholder="Enter fileId"
          value={fileIdInput}
          onChange={(event) => setFileIdInput(event.target.value)}
          disabled={Boolean(downloadingId)}
        />
        <button className="primary-button" type="submit" disabled={Boolean(downloadingId)}>
          {downloadingId && downloadingId === fileIdInput.trim() ? "Downloading..." : "Download"}
        </button>
      </form>

      <div className="section-head">
        <h3>Recent Uploads</h3>
        <button className="ghost-button" type="button" onClick={() => setRefreshKey((v) => v + 1)}>
          Refresh
        </button>
      </div>

      {recentFiles.length === 0 ? (
        <article className="placeholder-panel">No uploads recorded yet.</article>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>File ID</th>
                <th>Filename</th>
                <th>Uploaded At</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {recentFiles.map((item) => (
                <tr key={item.fileId}>
                  <td>{item.fileId}</td>
                  <td>{item.filename || "-"}</td>
                  <td>{formatTime(item.uploadedAt)}</td>
                  <td>
                    <button
                      className="ghost-button"
                      type="button"
                      onClick={() => startDownload(item.fileId, item.filename)}
                      disabled={Boolean(downloadingId)}
                    >
                      {downloadingId === item.fileId ? "Downloading..." : "Download"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {success ? <p className="message success">{success}</p> : null}
      {error ? <p className="message error">{error}</p> : null}
    </section>
  );
}
