# UI Bug Fixes - Frontend Implementation Guide

This document provides detailed fixes for the 4 reported UI bugs in the Skillama LMS platform.

## Bug 1: Pause Button Not Clickable When Subtitles Appear

### Problem
When audio lecture plays and subtitles appear, the subtitle overlay blocks the pause button, making it unclickable.

### Root Cause
- Subtitles are positioned with a higher z-index or overlapping the pause button area
- Subtitle container doesn't account for media controls positioning

### Solution

#### CSS Fix:
```css
/* Ensure pause button and media controls have higher z-index than subtitles */
.media-player-controls {
  position: relative;
  z-index: 1000; /* Higher than subtitle container */
  pointer-events: auto; /* Ensure click events work */
}

.pause-button,
.play-button {
  position: relative;
  z-index: 1001; /* Even higher than controls container */
  pointer-events: auto;
  background-color: rgba(0, 0, 0, 0.7); /* Semi-transparent background for visibility */
  border-radius: 50%;
  padding: 12px;
  cursor: pointer;
  min-width: 48px; /* Minimum touch target size */
  min-height: 48px;
}

/* Subtitle container should not overlap controls */
.subtitle-container {
  position: absolute;
  bottom: 80px; /* Adjust based on controls height - leave space for controls */
  left: 0;
  right: 0;
  z-index: 100; /* Lower than controls */
  padding: 10px 20px;
  pointer-events: none; /* Subtitles should not block clicks */
}

.subtitle-text {
  display: inline-block;
  background-color: rgba(0, 0, 0, 0.7);
  color: #ffffff;
  padding: 8px 16px;
  border-radius: 4px;
  max-width: 80%;
  margin: 0 auto;
  text-align: center;
}
```

#### HTML Structure Fix:
```html
<div class="lecture-player-container">
  <!-- PPT Image/Slide -->
  <div class="ppt-slide-container">
    <!-- Slide content -->
  </div>
  
  <!-- Media Player with Controls -->
  <div class="media-player-wrapper">
    <div class="media-player-controls">
      <button class="pause-button" id="pauseBtn">
        <svg><!-- Pause icon --></svg>
      </button>
      <div class="playback-speed">1x</div>
      <!-- Other controls -->
    </div>
    
    <!-- Subtitles positioned above controls but below clickable area -->
    <div class="subtitle-container">
      <div class="subtitle-text" id="subtitleText">
        <!-- Subtitle content -->
      </div>
    </div>
  </div>
</div>
```

#### JavaScript Enhancement (if needed):
```javascript
// Ensure pause button is always clickable
document.addEventListener('DOMContentLoaded', function() {
  const pauseButton = document.getElementById('pauseBtn');
  const subtitleContainer = document.querySelector('.subtitle-container');
  
  // Make sure subtitles don't interfere with button clicks
  if (pauseButton && subtitleContainer) {
    subtitleContainer.style.pointerEvents = 'none';
    pauseButton.style.pointerEvents = 'auto';
    
    // Add click handler with event propagation control
    pauseButton.addEventListener('click', function(e) {
      e.stopPropagation();
      e.preventDefault();
      // Toggle play/pause logic
      togglePlayPause();
    }, true); // Use capture phase
  }
});
```

---

## Bug 2: Chat Query Hidden Behind PPT Image

### Problem
The chat query interface is hidden behind the PPT/slide image, making it inaccessible to users.

### Root Cause
- Chat panel has lower z-index than PPT image
- Chat panel might be positioned absolutely but behind the slide
- Layout doesn't account for chat panel visibility

### Solution

#### CSS Fix:
```css
/* PPT Slide Container - should not cover chat */
.ppt-slide-container {
  position: relative;
  z-index: 1; /* Lower z-index */
  width: 70%; /* Adjust width to leave space for chat */
  float: left; /* Or use flexbox */
  margin-right: 20px;
}

/* Chat Query Panel - should be visible and accessible */
.chat-query-panel {
  position: relative; /* or fixed if you want it to stay in view */
  z-index: 1000; /* Higher than PPT image */
  width: 30%; /* Adjust based on design */
  float: right; /* Or use flexbox */
  background-color: #ffffff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  max-height: 600px;
  overflow-y: auto;
}

/* Alternative: Overlay chat panel */
.chat-query-panel.overlay {
  position: fixed;
  top: 20px;
  right: 20px;
  width: 350px;
  z-index: 2000; /* Very high z-index */
  background-color: #ffffff;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
}

/* Flexbox Layout Alternative */
.lecture-content-wrapper {
  display: flex;
  flex-direction: row;
  gap: 20px;
  align-items: flex-start;
}

.ppt-slide-container {
  flex: 1;
  z-index: 1;
}

.chat-query-panel {
  flex: 0 0 350px; /* Fixed width */
  z-index: 1000;
  position: sticky;
  top: 20px; /* Sticky positioning */
}
```

#### HTML Structure Fix:
```html
<div class="lecture-content-wrapper">
  <!-- PPT Slide - Left side -->
  <div class="ppt-slide-container">
    <img src="slide-image.jpg" alt="Lecture Slide" />
    <!-- Slide content -->
  </div>
  
  <!-- Chat Query Panel - Right side, always visible -->
  <div class="chat-query-panel" id="chatPanel">
    <div class="chat-header">
      <h3>Chat with AI Tutor</h3>
      <button class="chat-toggle-btn" id="chatToggle">−</button>
    </div>
    <div class="chat-messages" id="chatMessages">
      <!-- Chat messages -->
    </div>
    <div class="chat-input-container">
      <input type="text" id="chatInput" placeholder="Ask a question..." />
      <button id="sendBtn">Send</button>
    </div>
  </div>
</div>
```

#### JavaScript Enhancement:
```javascript
// Ensure chat panel is always accessible
document.addEventListener('DOMContentLoaded', function() {
  const chatPanel = document.getElementById('chatPanel');
  const pptContainer = document.querySelector('.ppt-slide-container');
  
  // Ensure chat panel z-index is higher
  if (chatPanel && pptContainer) {
    chatPanel.style.zIndex = '1000';
    pptContainer.style.zIndex = '1';
  }
  
  // Optional: Toggle chat panel visibility
  const chatToggle = document.getElementById('chatToggle');
  if (chatToggle) {
    chatToggle.addEventListener('click', function() {
      chatPanel.classList.toggle('collapsed');
    });
  }
});
```

---

## Bug 3: Segment Tabs Color Coding Not Visible Per Theme

### Problem
The color coding of segments (AI-Tutor, Code Execution, Code Debug, Chat) is not visible as per the application theme.

### Root Cause
- Active/inactive states don't have sufficient contrast
- Colors don't match the application's theme
- Inactive tabs are too faded/greyed out

### Solution

#### CSS Fix:
```css
/* Segment Tabs Container */
.segment-tabs-container {
  display: flex;
  gap: 8px;
  border-bottom: 2px solid #e0e0e0;
  padding: 0 20px;
  background-color: #ffffff;
}

/* Individual Segment Tab */
.segment-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: #666666; /* Inactive text color */
  border-bottom: 3px solid transparent;
  transition: all 0.3s ease;
  position: relative;
}

/* Active Segment Tab - Use theme colors */
.segment-tab.active {
  color: #1976d2; /* Primary theme color - adjust to match your theme */
  border-bottom-color: #1976d2;
  background-color: rgba(25, 118, 210, 0.08); /* Light background tint */
  font-weight: 600;
}

/* Hover State */
.segment-tab:hover:not(.active) {
  color: #424242;
  background-color: rgba(0, 0, 0, 0.04);
}

/* Segment Tab Icons */
.segment-tab .icon {
  width: 20px;
  height: 20px;
  opacity: 0.7;
  transition: opacity 0.3s ease;
}

.segment-tab.active .icon {
  opacity: 1;
  color: #1976d2; /* Match active text color */
}

/* Specific segment colors (if you want different colors per segment) */
.segment-tab[data-segment="ai-tutor"].active {
  color: #1976d2; /* Blue for AI-Tutor */
  border-bottom-color: #1976d2;
}

.segment-tab[data-segment="code-execution"].active {
  color: #2e7d32; /* Green for Code Execution */
  border-bottom-color: #2e7d32;
}

.segment-tab[data-segment="code-debug"].active {
  color: #f57c00; /* Orange for Debug */
  border-bottom-color: #f57c00;
}

.segment-tab[data-segment="chat"].active {
  color: #7b1fa2; /* Purple for Chat */
  border-bottom-color: #7b1fa2;
}

/* Ensure inactive tabs are visible but distinct */
.segment-tab:not(.active) {
  color: #757575; /* Medium grey - visible but clearly inactive */
  opacity: 1; /* Don't fade too much */
}
```

#### HTML Structure:
```html
<div class="segment-tabs-container">
  <button class="segment-tab active" data-segment="ai-tutor" id="aiTutorTab">
    <svg class="icon"><!-- AI Tutor icon --></svg>
    <span>AI-Tutor</span>
  </button>
  
  <button class="segment-tab" data-segment="code-execution" id="codeExecutionTab">
    <svg class="icon"><!-- Code Execution icon --></svg>
    <span>Code Execution</span>
  </button>
  
  <button class="segment-tab" data-segment="code-debug" id="codeDebugTab">
    <svg class="icon"><!-- Debug icon --></svg>
    <span>Debug</span>
  </button>
  
  <button class="segment-tab" data-segment="chat" id="chatTab">
    <svg class="icon"><!-- Chat icon --></svg>
    <span>Chat</span>
  </button>
</div>
```

#### JavaScript for Tab Switching:
```javascript
// Handle segment tab clicks
document.querySelectorAll('.segment-tab').forEach(tab => {
  tab.addEventListener('click', function() {
    // Remove active class from all tabs
    document.querySelectorAll('.segment-tab').forEach(t => {
      t.classList.remove('active');
    });
    
    // Add active class to clicked tab
    this.classList.add('active');
    
    // Show corresponding content
    const segment = this.getAttribute('data-segment');
    showSegmentContent(segment);
  });
});

function showSegmentContent(segment) {
  // Hide all segment contents
  document.querySelectorAll('.segment-content').forEach(content => {
    content.style.display = 'none';
  });
  
  // Show selected segment content
  const selectedContent = document.getElementById(`${segment}-content`);
  if (selectedContent) {
    selectedContent.style.display = 'block';
  }
}
```

---

## Bug 4: Subtitle Highlight Color in Blue Shades Makes Text Hard to Read

### Problem
The subtitle highlight color uses blue shades which makes the text harder to read, especially against dark backgrounds.

### Root Cause
- Blue text on blue highlight creates poor contrast
- Color choice doesn't account for readability
- No sufficient background contrast

### Solution

#### CSS Fix:
```css
/* Subtitle Container */
.subtitle-container {
  position: absolute;
  bottom: 80px; /* Above media controls */
  left: 0;
  right: 0;
  z-index: 100;
  padding: 10px 20px;
  display: flex;
  justify-content: center;
  align-items: center;
}

/* Subtitle Text - High contrast, readable colors */
.subtitle-text {
  display: inline-block;
  background-color: rgba(0, 0, 0, 0.85); /* Dark semi-transparent background */
  color: #ffffff; /* White text for maximum contrast */
  padding: 12px 20px;
  border-radius: 6px;
  max-width: 80%;
  text-align: center;
  font-size: 16px;
  font-weight: 500;
  line-height: 1.5;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.1); /* Subtle border for definition */
}

/* Current/Active Subtitle Highlight - Use contrasting color */
.subtitle-text.highlighted {
  background-color: rgba(255, 193, 7, 0.9); /* Yellow/Amber highlight - high contrast */
  color: #000000; /* Black text on yellow background */
  font-weight: 600;
  border: 2px solid rgba(255, 193, 7, 1);
}

/* Alternative: White highlight with dark text */
.subtitle-text.highlighted.alternative {
  background-color: rgba(255, 255, 255, 0.95); /* White highlight */
  color: #000000; /* Black text */
  border: 2px solid #ffffff;
}

/* Alternative: Green highlight (good contrast) */
.subtitle-text.highlighted.green {
  background-color: rgba(76, 175, 80, 0.9); /* Green highlight */
  color: #ffffff; /* White text */
  border: 2px solid rgba(76, 175, 80, 1);
}

/* Ensure text is always readable */
.subtitle-text::selection {
  background-color: rgba(255, 255, 255, 0.3);
  color: #ffffff;
}
```

#### HTML Structure:
```html
<div class="subtitle-container">
  <div class="subtitle-text" id="currentSubtitle">
    <!-- Current subtitle text -->
  </div>
</div>
```

#### JavaScript for Highlight Management:
```javascript
// Function to update subtitle with proper highlighting
function updateSubtitle(text, isHighlighted = false) {
  const subtitleElement = document.getElementById('currentSubtitle');
  if (subtitleElement) {
    subtitleElement.textContent = text;
    
    // Remove all highlight classes
    subtitleElement.classList.remove('highlighted', 'alternative', 'green');
    
    // Add highlight class if needed
    if (isHighlighted) {
      // Use yellow/amber highlight for best readability
      subtitleElement.classList.add('highlighted');
      // Or use alternative: subtitleElement.classList.add('highlighted', 'alternative');
      // Or use green: subtitleElement.classList.add('highlighted', 'green');
    }
  }
}

// Example: When subtitle changes during playback
audioPlayer.addEventListener('timeupdate', function() {
  const currentTime = audioPlayer.currentTime;
  const currentSubtitle = getSubtitleForTime(currentTime);
  
  if (currentSubtitle) {
    updateSubtitle(currentSubtitle.text, true); // Highlight current subtitle
  }
});
```

#### Alternative Color Schemes (Choose based on your theme):

**Option 1: Yellow/Amber (Recommended for readability)**
```css
.subtitle-text.highlighted {
  background-color: #ffc107; /* Amber */
  color: #000000;
}
```

**Option 2: White (High contrast)**
```css
.subtitle-text.highlighted {
  background-color: #ffffff;
  color: #000000;
}
```

**Option 3: Green (Good contrast, modern)**
```css
.subtitle-text.highlighted {
  background-color: #4caf50; /* Green */
  color: #ffffff;
}
```

**Option 4: Orange (Warm, visible)**
```css
.subtitle-text.highlighted {
  background-color: #ff9800; /* Orange */
  color: #000000;
}
```

---

## Implementation Checklist

- [ ] **Bug 1**: Update media player CSS with proper z-index and positioning
- [ ] **Bug 1**: Ensure subtitle container doesn't overlap pause button
- [ ] **Bug 1**: Test pause button clickability with subtitles visible
- [ ] **Bug 2**: Adjust chat panel z-index and positioning
- [ ] **Bug 2**: Update layout to use flexbox or proper positioning
- [ ] **Bug 2**: Test chat panel visibility and accessibility
- [ ] **Bug 3**: Update segment tab CSS with theme-appropriate colors
- [ ] **Bug 3**: Ensure active/inactive states are clearly visible
- [ ] **Bug 3**: Test tab switching and color visibility
- [ ] **Bug 4**: Change subtitle highlight color from blue to high-contrast color
- [ ] **Bug 4**: Update subtitle text color for readability
- [ ] **Bug 4**: Test subtitle readability in various lighting conditions

## Testing Recommendations

1. **Cross-browser testing**: Test in Chrome, Firefox, Safari, Edge
2. **Responsive testing**: Test on different screen sizes
3. **Accessibility testing**: Ensure color contrast meets WCAG AA standards
4. **User testing**: Get feedback on readability and usability

## Notes

- Adjust color values (#1976d2, etc.) to match your application's theme
- Z-index values may need adjustment based on your existing CSS
- Consider using CSS variables for theme colors for easier maintenance
- Test all fixes together to ensure they don't conflict with each other
