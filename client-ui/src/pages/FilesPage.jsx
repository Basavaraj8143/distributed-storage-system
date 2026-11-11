import { Fragment, useCallback, useEffect, useState } from "react";
import { deleteFile, downloadFile, getFileDetails, listFiles } from "../api";
import { usePolling } from "../hooks/usePolling";

const POLL_MS = 10000;

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

function ReplicaBadge({ minReplicas, targetReplicas }) {
  let tone = "good";
  if (minReplicas <= 0) tone = "bad";
  else if (minReplicas < targetReplicas) tone = "warn";

  return (
    <span
      className={`status-dot ${tone}`}
      title="Lowest replica count across the file's chunks / target replicas"
    >
      {minReplicas}/{targetReplicas}
    </span>
  );
}

export default function FilesPage() {
  const [fileIdInput, setFileIdInput] = useState("");
  const [files, setFiles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [downloadingId, setDownloadingId] = useState("");
  const [deletingId, setDeletingId] = useState("");
  const [expandedId, setExpandedId] = useState("");
  const [detailsLoadingId, setDetailsLoadingId] = useState("");
  const [detailsById, setDetailsById] = useState({});
  const [success, setSuccess] = useState("");
  const [error, setError] = useState("");

  const busy = Boolean(downloadingId || deletingId);

  const loadFiles = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const result = await listFiles();
      setFiles(result);
      // Replica locations can change over time, so drop cached chunk details on every refresh.
      setDetailsById({});
    } catch (err) {
      setError(err.message || "Could not load files.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadFiles();
  }, [loadFiles]);

  // Background refresh so replica counts follow node failures and repairs without clicking Refresh.
  // It is silent: no spinner, no error banner, and it keeps the expanded row open.
  const silentRefresh = useCallback(async () => {
    if (downloadingId || deletingId) return;
    try {
      const result = await listFiles();
      setFiles(result);
      if (expandedId) {
        try {
          const details = await getFileDetails(expandedId);
          setDetailsById((prev) => ({ ...prev, [expandedId]: details }));
        } catch {
          // The file may have just been deleted; the next list refresh drops the row.
        }
      }
    } catch {
      // Keep showing the last good data. The manual Refresh button reports errors.
    }
  }, [downloadingId, deletingId, expandedId]);

  usePolling(silentRefresh, POLL_MS, { immediate: false });

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
      const downloadUrl = URL.createObjectURL(result.blob);
      const link = document.createElement("a");
      link.href = downloadUrl;
      const serverFilename = parseFilenameFromContentDisposition(result.contentDisposition);
      const fallbackName = `download-${fileId}${extensionFromContentType(result.contentType)}`;
      link.download = serverFilename || filenameHint || fallbackName;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(downloadUrl);
      setSuccess(`Download started for ${filenameHint || fileId}`);
    } catch (err) {
      setError(err.message || "Download failed.");
    } finally {
      setDownloadingId("");
    }
  }

  async function toggleDetails(fileId) {
    if (expandedId === fileId) {
      setExpandedId("");
      return;
    }

    setExpandedId(fileId);
    if (detailsById[fileId]) return;

    setDetailsLoadingId(fileId);
    setError("");
    try {
      const details = await getFileDetails(fileId);
      setDetailsById((prev) => ({ ...prev, [fileId]: details }));
    } catch (err) {
      setError(err.message || "Could not load file details.");
      setExpandedId("");
    } finally {
      setDetailsLoadingId("");
    }
  }

  async function handleDelete(file) {
    const label = file.filename || file.fileId;
    const confirmed = window.confirm(
      `Delete "${label}"?\n\nThis removes the file and all of its chunks from the storage nodes. It cannot be undone.`
    );
    if (!confirmed) return;

    setDeletingId(file.fileId);
    setError("");
    setSuccess("");

    try {
      await deleteFile(file.fileId);
      setFiles((prev) => prev.filter((item) => item.fileId !== file.fileId));
      setDetailsById((prev) => {
        const next = { ...prev };
        delete next[file.fileId];
        return next;
      });
      if (expandedId === file.fileId) setExpandedId("");
      setSuccess(`Deleted ${label}`);
    } catch (err) {
      setError(err.message || "Delete failed.");
    } finally {
      setDeletingId("");
    }
  }

  return (
    <section className="page">
      <p className="eyebrow">Core Flow</p>
      <h2>Files</h2>
      <p className="page-copy">
        Every file stored in the cluster. Download, inspect where its chunks live, or delete it.
      </p>

      <form
        className="action-panel inline-form"
        onSubmit={(event) => {
          event.preventDefault();
          startDownload(fileIdInput.trim());
        }}
      >
        <label className="field-label" htmlFor="file-id-input">
          Download by file ID
        </label>
        <input
          id="file-id-input"
          className="text-input"
          type="text"
          placeholder="Enter fileId"
          value={fileIdInput}
          onChange={(event) => setFileIdInput(event.target.value)}
          disabled={busy}
        />
        <button className="primary-button" type="submit" disabled={busy}>
          {downloadingId && downloadingId === fileIdInput.trim() ? "Downloading..." : "Download"}
        </button>
      </form>

      <div className="section-head">
        <h3>
          Stored Files{!loading ? ` (${files.length})` : ""}
          <span className="field-note"> · updates every {POLL_MS / 1000}s</span>
        </h3>
        <button className="ghost-button" type="button" onClick={loadFiles} disabled={loading || busy}>
          {loading ? "Loading..." : "Refresh"}
        </button>
      </div>

      {loading && files.length === 0 ? (
        <article className="placeholder-panel">Loading files...</article>
      ) : files.length === 0 ? (
        <article className="placeholder-panel">No files stored yet. Upload one from the Upload page.</article>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Filename</th>
                <th>File ID</th>
                <th>Chunks</th>
                <th>Replicas</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {files.map((file) => {
                const isExpanded = expandedId === file.fileId;
                const details = detailsById[file.fileId];

                return (
                  <Fragment key={file.fileId}>
                    <tr>
                      <td>{file.filename || "-"}</td>
                      <td className="mono-cell">{file.fileId}</td>
                      <td>{file.chunkCount}</td>
                      <td>
                        <ReplicaBadge minReplicas={file.minReplicas} targetReplicas={file.targetReplicas} />
                      </td>
                      <td>
                        <div className="row-actions">
                          <button
                            className="ghost-button small"
                            type="button"
                            onClick={() => startDownload(file.fileId, file.filename)}
                            disabled={busy}
                          >
                            {downloadingId === file.fileId ? "Downloading..." : "Download"}
                          </button>
                          <button
                            className="ghost-button small"
                            type="button"
                            onClick={() => toggleDetails(file.fileId)}
                            disabled={deletingId === file.fileId}
                          >
                            {isExpanded ? "Hide details" : "Details"}
                          </button>
                          <button
                            className="danger-button small"
                            type="button"
                            onClick={() => handleDelete(file)}
                            disabled={busy}
                          >
                            {deletingId === file.fileId ? "Deleting..." : "Delete"}
                          </button>
                        </div>
                      </td>
                    </tr>

                    {isExpanded ? (
                      <tr className="detail-row">
                        <td colSpan={5}>
                          {detailsLoadingId === file.fileId && !details ? (
                            <p className="field-note">Loading chunk details...</p>
                          ) : details ? (
                            <ul className="chunk-list">
                              {details.chunks.map((chunk) => {
                                const copies = chunk.nodeUrls.length;
                                const tone = copies <= 0 ? "lost" : copies < details.targetReplicas ? "under" : "";
                                return (
                                <li className={`chunk-item ${tone}`.trim()} key={chunk.chunkId}>
                                  <strong>
                                    Chunk {chunk.chunkIndex}
                                    {tone === "lost" ? <span className="chunk-flag bad">NO COPIES</span> : null}
                                    {tone === "under" ? <span className="chunk-flag warn">UNDER-REPLICATED</span> : null}
                                  </strong>
                                  <span className="chunk-meta">id: {chunk.chunkId}</span>
                                  <span className="chunk-meta">
                                    sha256: {chunk.checksum ? `${chunk.checksum.slice(0, 16)}...` : "-"}
                                  </span>
                                  <span className="chunk-meta">
                                    nodes ({chunk.nodeUrls.length}/{details.targetReplicas}):{" "}
                                    {chunk.nodeUrls.length > 0 ? chunk.nodeUrls.join(", ") : "none"}
                                  </span>
                                </li>
                                );
                              })}
                            </ul>
                          ) : null}
                        </td>
                      </tr>
                    ) : null}
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {success ? <p className="message success">{success}</p> : null}
      {error ? <p className="message error">{error}</p> : null}
    </section>
  );
}
