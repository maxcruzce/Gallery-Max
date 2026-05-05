# Project Tasks: HyperGallery Fixes & Enhancements

## Phase 1: UI & Search Enhancements
- [ ] **Tab Change Transitions**: Implement smooth transitions between "Photos", "Albums", and "Moments" tabs to prevent showing stale content while loading.
- [ ] **Album Results in Search**:
    - [ ] Update `GalleryViewModel` to include albums in search results.
    - [ ] Create a horizontal scrollable section at the top of search results for matching albums.
    - [ ] Implement conditional visibility (hide section if no albums match).

## Phase 2: Album Management Fixes
- [ ] **Virtual Album Creation**:
    - [ ] Debug `MediaRepository.createAlbum` and `addAlbumToVirtual`.
    - [ ] Ensure virtual albums are correctly persisted and loaded.
- [ ] **Album Display Logic**:
    - [ ] Investigate why regular folders are appearing in the "Main Albums" section.
    - [ ] Enforce correct filtering in `GalleryViewModel.loadMedia` (Main vs. Other albums).
- [ ] **Album Pinning**:
    - [ ] Fix `toggleAlbumPinned` functionality.
    - [ ] Ensure pinned status is saved in SharedPreferences/Database and correctly sorted in UI.
