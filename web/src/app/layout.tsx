import React from 'react';

export const metadata = {
  title: 'Peregrine - Fleet Console',
  description: 'Android mock-location fleet control, kinematics simulation, and telemetry console.',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="antialiased bg-slate-950 font-sans">{children}</body>
    </html>
  );
}
