'use client';

import config from "@/../next.config"

export const basePath = config.basePath ?? "";

export function goPath(path: string, newTab: boolean = false) {
    if (newTab)
        window.open(basePath + path, "_blank")?.focus();
    else
        window.location.href = basePath + path;
}

export function refreshPage() {
    window.location.reload();
}