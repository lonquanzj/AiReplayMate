const owner = "lonquanzj";
const repo = "AiReplayMate";
const latestReleaseUrl = `https://github.com/${owner}/${repo}/releases/latest`;
const apiUrl = `https://api.github.com/repos/${owner}/${repo}/releases/latest`;

const downloadButton = document.querySelector("#downloadButton");
const releaseMeta = document.querySelector("#releaseMeta");

function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) {
    return "";
  }

  const units = ["B", "KB", "MB", "GB"];
  let size = bytes;
  let unitIndex = 0;

  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }

  return `${size.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

function setFallback(message) {
  downloadButton.href = latestReleaseUrl;
  downloadButton.textContent = "打开最新 Release";
  releaseMeta.textContent = message;
}

async function loadLatestRelease() {
  try {
    const response = await fetch(apiUrl, {
      headers: {
        Accept: "application/vnd.github+json"
      }
    });

    if (!response.ok) {
      setFallback("还没有可直接下载的 Release，点击按钮查看发布页。");
      return;
    }

    const release = await response.json();
    const apkAsset = Array.isArray(release.assets)
      ? release.assets.find((asset) => asset.name.toLowerCase().endsWith(".apk"))
      : null;

    if (!apkAsset) {
      setFallback(`${release.tag_name || "最新版本"} 暂未附加 APK，点击按钮查看发布页。`);
      return;
    }

    const size = formatBytes(apkAsset.size);
    downloadButton.href = apkAsset.browser_download_url;
    downloadButton.textContent = `下载 ${apkAsset.name}`;
    releaseMeta.textContent = [
      release.tag_name || "最新版本",
      size,
      release.published_at ? new Date(release.published_at).toLocaleDateString("zh-CN") : ""
    ].filter(Boolean).join(" · ");
  } catch (error) {
    setFallback("无法读取 GitHub Release 信息，点击按钮进入发布页。");
  }
}

loadLatestRelease();
