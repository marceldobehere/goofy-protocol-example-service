import { Geist, Geist_Mono } from "next/font/google";
import styles from "./layout.module.css";
import "./globals.css";
import {Metadata} from "next";
import SettingsModal from "./components/settings-modal/component";


const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Goofy IRC",
  description: "Example IRC Service for Goofy Protocol",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
    return (
    <html lang="en" className={`${geistSans.variable} ${geistMono.variable}`}>
      <body>
      {children}

      <div id={"main-loading-div"} style={{display: "none", zIndex: 150}}></div>
      <button id={styles.FloatingSettingsButton}
          // @ts-expect-error custom attributes
          command={"show-modal"} commandfor={styles.FloatingSettings}>&#x2699;</button>
      <dialog id={styles.FloatingSettings}>
          <button
              // @ts-expect-error custom attributes
              command={"close"} commandfor={styles.FloatingSettings}>X</button>
          <SettingsModal></SettingsModal>
      </dialog>
      </body>
    </html>
  );
}
