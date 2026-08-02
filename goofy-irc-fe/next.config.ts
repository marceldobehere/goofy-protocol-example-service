import type { NextConfig } from "next";

const nextConfig: NextConfig = {
    output: "export",
    trailingSlash: true,
    basePath: "/goofy-protocol-example-service",
    images: {
        unoptimized: true
    }
};

export default nextConfig;
