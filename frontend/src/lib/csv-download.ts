export function escapeCsvCell(value: string): string {
  return `"${value.replace(/"/g, '""')}"`
}

export function triggerBlobDownload(blob: Blob, fileName: string): void {
  const downloadUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = downloadUrl
  anchor.download = fileName

  try {
    document.body.append(anchor)
    anchor.click()
  } finally {
    anchor.remove()
    window.setTimeout(() => URL.revokeObjectURL(downloadUrl), 1000)
  }
}
