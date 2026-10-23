# Legacy screen background regression

The first production 1.21.5 fullscreen session test saved a playable 3.3-second
1280×720 MP4 when disconnecting from a world. Visual inspection nevertheless
found the logo, card body and custom labels blurred while widget labels stayed
sharp. That artifact must not be published as visually approved.

Inspection of the mapped Minecraft 1.21.5 Screen.render bytecode confirmed it
calls renderBackground before rendering children. Volt draws the library before
calling super.render, so that inherited callback blurred the content already
painted. The same inherited call is present in 1.21.4. Newer inspected versions
1.21.6, 1.21.8 and 1.21.9 do not invoke that callback from Screen.render.

VoltScreen now overrides renderBackground as an empty method because Volt paints
an opaque background itself. Production artifacts need rebuilding and another
fullscreen visual check before their runtime receipts are used for publication.

The rebuilt 1.21.5 production artifact passed the fullscreen session test again
and visual inspection confirmed the logo, card bodies and labels remain sharp.
The checked artifact SHA-512 is recorded in
`docs/verification/ports/1.21.5/v7/validation-session.json`; its generated MP4 is
3.233333 seconds at 1280×720 with VAAPI encoding.
