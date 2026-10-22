# Accessibility

WorldBinder should be usable by everyone who plays Minecraft, including people who use a keyboard instead of a mouse, need higher contrast or larger text, are sensitive to motion or sound, or have color vision deficiencies. This document describes our commitment, what WorldBinder currently supports, its known limitations, and how to report barriers.

## Our commitment

- We treat accessibility barriers as bugs, not as feature requests.
- New screens and widgets must work with the keyboard, stay readable in high-contrast mode, respect reduced motion and never convey information by color alone.
- We document known limitations honestly in this file and update it with every release that changes the interface.
- We welcome feedback from people with disabilities and prioritize reports that block someone from using WorldBinder.

WorldBinder follows the intent of the [Web Content Accessibility Guidelines (WCAG) 2.2](https://www.w3.org/TR/WCAG22/) where they apply to a game interface (contrast, keyboard operation, motion, text alternatives). WorldBinder is a Minecraft mod, not a website, and has not been formally audited against WCAG.

## Supported environments

| Area | Supported |
| ---- | --------- |
| Minecraft | Java Edition 26.1, 26.1.1, 26.1.2, 26.2 and 26.3 with Fabric Loader 0.19.5+ and Fabric API |
| Operating systems | Windows, macOS and Linux (everything Minecraft Java Edition supports) |
| Input | Mouse and keyboard; all main functions also work with the keyboard alone |
| Languages | English and German |
| Display | Any window size and GUI scale; WorldBinder menus use a fixed virtual canvas that scales with the window |

## Accessibility features

Most options are in **Settings > Interface** (open WorldBinder with F9):

- **High contrast**: stronger borders, text and focus colors.
- **Reduced motion**: turns off animations, transitions, pulsing markers and smooth camera movement. Animation speed can also be adjusted, and screen transitions can be disabled separately.
- **Keyboard navigation**: Tab and Shift+Tab move between controls with a visible focus ring, Enter/Space activate, Ctrl+Tab switches settings sections, Ctrl+S saves, Esc closes. The live map supports WASD/arrow keys, +/- zoom, Space (center on player), F (follow), G (grid), H (sidebar), E (explore), V (3D view), P (waypoint), and in 3D `,` / `.` to rotate and Page Up/Down to tilt.
- **Tooltips**: every control has a tooltip with a description; the delay can be adjusted, and shortcut hints can be shown or hidden. Keyboard focus also shows tooltips.
- **Text and color**: theme accents including a custom color, adjustable background dimming, and option to show or hide icons. Status information on the map and HUD is always also available as text (tooltips, sidebar, status line), not only as color.
- **Sound**: interface sounds and notification sounds can be turned off or set to any volume; no information is conveyed only through sound.
- **Notifications**: messages can be shown as chat messages, toasts, both or not at all, with adjustable notification size and duration.
- **HUD widgets**: every widget can be resized, moved, recolored or hidden in the Widget Editor.

## Known limitations

We know about these barriers and are working on them where possible:

- **Screen readers**: WorldBinder's custom widgets are drawn by the mod and are not yet fully announced by Minecraft's Narrator. Button labels are provided, but values such as slider positions, map details and HUD contents are not read aloud.
- **Live map**: the map and the 3D view are visual by nature. Chunk status uses colors plus icons, tooltips and the sidebar, but there is no text-only alternative for browsing the map.
- **3D view and drag gestures**: rotating, tilting and panning are easiest with a mouse; keyboard alternatives exist (see above) but there is no keyboard way yet to move a "cursor" over the map to inspect single chunks.
- **Text size**: menus scale with the window. In small windows text can become small; a separate text size option does not exist yet.
- **Color vision**: theme accents are selectable, but there is no dedicated color-blind palette for map status colors yet.
- **Movement Tools** change how the player moves and can be disorienting; they are off by default.
- **Languages**: only English and German are available.

## Reporting a barrier

If something in WorldBinder is hard or impossible for you to use, please tell us:

- Open an issue at https://github.com/Philiipp06/WorldBinder/issues and add "Accessibility" to the title, or
- write in the [WorldBinder Discord](https://discord.com/invite/V7h3vaXxDZ), or send a direct message to the maintainer if you prefer not to post publicly.

Helpful details: what you were trying to do, which screen or feature, what assistive technology or settings you use (for example high contrast, GUI scale, screen reader, keyboard only), and your Minecraft and WorldBinder versions. You do not need to know the technical cause.

We aim to respond to accessibility reports within 7 days. Barriers that block core tasks (capturing, exporting, closing a screen, changing settings) are treated with high priority.

## For contributors

Please check interface changes against this list before opening a pull request (see also [CONTRIBUTING.md](CONTRIBUTING.md)):

- [ ] Every new control can be reached with Tab and activated with Enter/Space, and shows a focus ring.
- [ ] The control has a clear label and a tooltip; icon-only buttons have a text label for tooltips and narration.
- [ ] Text stays readable in high-contrast mode and on light and dark backgrounds; aim for a contrast ratio of at least 4.5:1 for text.
- [ ] Information is never shown by color alone (add an icon, text or pattern).
- [ ] Animations respect reduced motion and the animation speed setting; nothing flashes more than three times per second.
- [ ] Sounds go through the interface sound settings and are never the only feedback.
- [ ] Layouts work at small window sizes and high GUI scales without overlapping or cut-off text.
- [ ] New texts exist in English and German.

## Further reading

- [Accessibility best practices for your project](https://opensource.guide/accessibility-best-practices-for-your-project/) (Open Source Guides)
- [Open Source Accessibility toolkit](https://github.com/open-source-accessibility/accessibility-toolkit)

*Last reviewed: October 2026, WorldBinder 1.6.2.*
