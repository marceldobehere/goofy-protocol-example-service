'use client';

function downloadBase64File(base64DataStr: string, filename: string) {
    const linkSource = base64DataStr;
    const downloadLink = document.createElement("a");
    downloadLink.href = linkSource;
    downloadLink.download = filename;
    downloadLink.click();
}

export function downloadTextfile(text: string, filename: string) {
    downloadBase64File("data:text/plain;charset=utf-8," + encodeURIComponent(text), filename);
}

export function downloadObjFile(obj: object, filename: string) {
    downloadBase64File("data:text/plain;charset=utf-8," + encodeURIComponent(JSON.stringify(obj)), filename);
}

export function downloadBinaryFile(data: Uint8Array, filename: string) {
    const blob = new Blob([data as BlobPart]);
    const url = URL.createObjectURL(blob);

    const downloadLink = document.createElement("a");
    downloadLink.href = url;
    downloadLink.download = filename;
    downloadLink.click();

    URL.revokeObjectURL(url);
}

export async function uploadData(multiple: boolean = false, type: string = "file"): Promise<FileList | File | null> {
    return await new Promise((resolve, reject) => {
        const fileInput = document.createElement("input");
        fileInput.type = type;
        fileInput.multiple = multiple;
        fileInput.onchange = () => {
            if (fileInput.files == null || fileInput.files.length == 0)
                resolve(null);
            else if (multiple)
                resolve(fileInput.files);
            else
                resolve(fileInput.files.item(0));
        };
        fileInput.onabort = () => {
            resolve(null);
        };
        fileInput.oncancel = () => {
            resolve(null);
        };
        fileInput.onclose = () => {
            resolve(null);
        };
        fileInput.onerror = (e) => {
            reject(e);
        }
        fileInput.click();
    });
}

export async function readFileStr(file: File): Promise<string> {
    return await new Promise((res, rej) => {
        const reader = new FileReader();
        reader.onload = () => {
            res(reader.result as string);
        };
        reader.onerror = (e) => {
            rej(e);
        };
        reader.readAsText(file);
    });
}

export async function readFileBytes(file: File): Promise<Uint8Array> {
    return await new Promise((res, rej) => {
        const reader = new FileReader();
        reader.onload = () => {
            res(new Uint8Array(reader.result as ArrayBuffer));
        };
        reader.onerror = (e) => rej(e);
        reader.readAsArrayBuffer(file);
    });
}

export async function readJsonFile<T>(file: File): Promise<T | null> {
    try {
        return JSON.parse(await readFileStr(file)) as T;
    } catch {
        return null;
    }
}