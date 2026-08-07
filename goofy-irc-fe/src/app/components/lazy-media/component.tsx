'use client';

import {useState} from "react";
import {useAsyncEffect} from "@/libs/global-state";
import {BucketData, getFisBucketData} from "@/app/user/home/fis-utils";
import {downloadBinaryFile} from "@/libs/file-utils";

const dataMap: Map<string, BucketData> = new Map();
export default function Component({mediaPath, roomServerUrl, enforceSize}: {mediaPath: string, roomServerUrl: string, enforceSize: string | null}) {
    const [data, setData] = useState<BucketData | null>(null);
    const [dataState, setDataState] = useState<"LOADING" | "ERROR" | "SUCCESS">("LOADING");

    useAsyncEffect(async () => {
        // console.debug("Lazy Media Component", mediaPath, roomServerUrl);
        const key = `${mediaPath}_@_${roomServerUrl}`;
        if (dataMap.has(key)) {
               setData(dataMap.get(key)!);
               setDataState("SUCCESS");
        } else {
            try {
                const res = await getFisBucketData(mediaPath, roomServerUrl);
                dataMap.set(key, res);
                setData(res);
                setDataState("SUCCESS");
            } catch (e) {
                console.error("Failed to load FIS Bucket Data for", mediaPath, roomServerUrl, e);
                setDataState("ERROR");
            }
        }
    }, []);

    function getCorrectElem() {
        if (data == null)
            return (<>??</>)

        const ct = (data.details?.contentType || "").toLowerCase();
        const src = data.blobUrl;
        const style = {display: "inline-block", padding: "0", margin: "0", maxWidth: "30rem", maxHeight: "10rem"} as React.CSSProperties;
        if (enforceSize != null) {
            style.width = enforceSize;
            style.height = enforceSize;
            style.objectFit = "cover";
        }
        const onClick = () => {window.open(src, "_blank");};

        // Very common cases:
        if (ct.startsWith("image/"))
            // eslint-disable-next-line @next/next/no-img-element
            return (<img onClick={onClick} alt={data.details.filename} src={src} style={style} />);

        if (ct.startsWith("video/"))
            return (<video src={src} style={style} muted controls/>);

        if (ct.startsWith("audio/"))
            return (<audio src={src} controls />);

        return (<a href={"#"} onClick={() => {
            downloadBinaryFile(data.blob as unknown as Uint8Array, data.details.filename);
        }}>File &quot;{data.details.filename}&quot;</a>);
    }

    return dataState === "SUCCESS" ? getCorrectElem() :
        <div style={{width:'1rem', height:'1rem', display:'inline-block'}}>
            {dataState === "LOADING" ? <span style={{color: "yellow"}}>?</span> : <span style={{color: "red"}}>X</span>}
        </div>;
}