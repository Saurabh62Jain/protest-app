// Global Application State
let token = localStorage.getItem('protest_token') || '';
let userRole = localStorage.getItem('protest_role') || '';
let userMobile = localStorage.getItem('protest_mobile') || '';

const API_BASE = '/api/v1';

// Toast Utility
function showToast(message, type = 'success') {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerHTML = `
    <span>${type === 'success' ? '⚡' : '⚠️'}</span>
    <div>${message}</div>
  `;
  container.appendChild(toast);
  setTimeout(() => {
    toast.remove();
  }, 4000);
}

// Initialize Application on Load
document.addEventListener('DOMContentLoaded', () => {
  // Register Service Worker for PWA mobile installation support
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js')
      .then(reg => console.log('Service Worker registered scope:', reg.scope))
      .catch(err => console.error('Service Worker registration failed:', err));
  }

  if (token) {
    setupDashboard();
  } else {
    showAuthOverlay();
  }

  // Bind Form Submissions
  document.getElementById('login-phone-form').addEventListener('submit', handlePhoneSubmit);
  document.getElementById('login-otp-form').addEventListener('submit', handleOtpSubmit);
  document.getElementById('voter-link-form').addEventListener('submit', handleVoterLinkSubmit);
  document.getElementById('raise-issue-form').addEventListener('submit', handleRaiseIssueSubmit);
  document.getElementById('create-survey-form').addEventListener('submit', handleCreateSurveySubmit);
  document.getElementById('create-bulletin-form').addEventListener('submit', handleCreateBulletinSubmit);

  // Initialize Settings Fields
  const settingClientId = document.getElementById('setting-g-client-id');
  const settingMapsKey = document.getElementById('setting-g-maps-key');
  if (settingClientId) settingClientId.value = localStorage.getItem('google_client_id') || '';
  if (settingMapsKey) settingMapsKey.value = localStorage.getItem('google_maps_key') || '';

  renderGoogleSignInButton();

  // Initialize dynamic survey builder fields
  addQuestionField();
  setDefaultSurveyExpiration();
});

// Authentication Flow
function showAuthOverlay() {
  document.getElementById('auth-overlay').style.display = 'flex';
  document.getElementById('voter-overlay').style.display = 'none';
}

function skipVoterLinking() {
  document.getElementById('voter-overlay').style.display = 'none';
  setupDashboard();
}
window.skipVoterLinking = skipVoterLinking;

function resetAuthFlow() {
  document.getElementById('login-phone-form').style.display = 'block';
  document.getElementById('login-otp-form').style.display = 'none';
}

async function handlePhoneSubmit(e) {
  e.preventDefault();
  const mobileInput = document.getElementById('login-mobile').value.trim();
  
  // Validation: must be exactly 10 digits
  const regex = /^\d{10}$/;
  if (!regex.test(mobileInput)) {
    showToast('Mobile number must be exactly 10 digits.', 'error');
    return;
  }
  
  const mobileNumber = `+91${mobileInput}`;
  
  try {
    const response = await fetch(`${API_BASE}/auth/otp/request`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mobileNumber })
    });
    
    if (response.ok) {
      userMobile = mobileNumber;
      showToast(`OTP requested for ${mobileNumber}. Check server logs!`);
      document.getElementById('login-phone-form').style.display = 'none';
      document.getElementById('login-otp-form').style.display = 'block';
    } else {
      const err = await response.text();
      showToast(err || 'Failed to request OTP', 'error');
    }
  } catch (error) {
    showToast('Connection error: ' + error.message, 'error');
  }
}

async function handleOtpSubmit(e) {
  e.preventDefault();
  const otpCode = document.getElementById('login-otp').value.trim();
  
  try {
    const response = await fetch(`${API_BASE}/auth/otp/verify`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mobileNumber: userMobile, otpCode })
    });

    if (response.ok) {
      const data = await response.json();
      token = data.token;
      userRole = data.role;
      
      localStorage.setItem('protest_token', token);
      localStorage.setItem('protest_role', userRole);
      localStorage.setItem('protest_mobile', userMobile);
      localStorage.setItem('protest_name', data.name || userMobile);
      
      showToast('Logged in successfully!');
      
      if (data.newUser) {
        document.getElementById('auth-overlay').style.display = 'none';
        document.getElementById('voter-overlay').style.display = 'flex';
      } else {
        document.getElementById('auth-overlay').style.display = 'none';
        setupDashboard();
      }
    } else {
      showToast('Invalid OTP. Please try again.', 'error');
    }
  } catch (error) {
    showToast('Login failed: ' + error.message, 'error');
  }
}

async function handleVoterLinkSubmit(e) {
  e.preventDefault();
  const voterId = document.getElementById('voter-id').value.trim();
  
  try {
    const response = await fetch(`${API_BASE}/auth/voter-id/link`, {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ voterId })
    });

    if (response.ok) {
      showToast('Voter ID linked successfully.');
      document.getElementById('voter-overlay').style.display = 'none';
      setupDashboard();
    } else {
      const err = await response.text();
      showToast(err || 'Failed to link Voter ID', 'error');
    }
  } catch (error) {
    showToast('Linking failed: ' + error.message, 'error');
  }
}

function logout() {
  localStorage.clear();
  token = '';
  userRole = '';
  userMobile = '';
  showAuthOverlay();
}

// Dashboard Configuration & Tabs
function setupDashboard() {
  const storedName = localStorage.getItem('protest_name') || userMobile;
  document.getElementById('user-display-mobile').innerText = storedName;
  const roleBadge = document.getElementById('user-display-role');
  roleBadge.innerText = userRole;
  
  // Reset badge class styles
  roleBadge.className = 'badge';
  if (userRole === 'ROLE_APPROVER') roleBadge.classList.add('approver');
  if (userRole === 'ROLE_ADMIN') roleBadge.classList.add('admin');

  // Toggle visible navigation modules
  document.getElementById('nav-raise-issue').style.display = userRole === 'ROLE_CITIZEN' ? 'block' : 'none';
  document.getElementById('nav-approver-actions').style.display = userRole === 'ROLE_APPROVER' ? 'block' : 'none';
  document.getElementById('nav-admin-panel').style.display = userRole === 'ROLE_ADMIN' ? 'block' : 'none';
  
  // Show bulletin editor only for politicians/approvers
  document.getElementById('create-bulletin-section').style.display = userRole === 'ROLE_APPROVER' ? 'block' : 'none';
  
  // Show survey creator only for appropriate accounts
  document.getElementById('create-survey-section').style.display = (userRole === 'ROLE_APPROVER' || userRole === 'ROLE_ADMIN') ? 'block' : 'none';

  // Load active panel contents
  switchTab('feed-panel', document.querySelector('.sidebar-link.active'));
}

function switchTab(panelId, linkElement) {
  // Toggle Navigation active classes
  document.querySelectorAll('.sidebar-link').forEach(link => link.classList.remove('active'));
  if (linkElement) linkElement.classList.add('active');

  // Toggle panel display
  document.querySelectorAll('.section-panel').forEach(panel => panel.classList.remove('active'));
  document.getElementById(panelId).classList.add('active');

  // Fetch contextual reports
  if (panelId === 'feed-panel') {
    detectUserLocationForFeed();
    setTimeout(() => {
      initMaps();
      if (feedMapInstance && feedMapInstance.invalidateSize) {
        feedMapInstance.invalidateSize();
      }
    }, 100);
  } else if (panelId === 'raise-panel') {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(position => {
        const lat = position.coords.latitude;
        const lng = position.coords.longitude;
        document.getElementById('issue-lat').value = lat.toFixed(6);
        document.getElementById('issue-lng').value = lng.toFixed(6);
        updateMapLocation(lat, lng, false);
      }, err => {});
    }
    setTimeout(() => {
      initMaps();
      if (raiseMapInstance && raiseMapInstance.invalidateSize) {
        raiseMapInstance.invalidateSize();
      }
    }, 100);
  } else if (panelId === 'approver-panel') {
    loadApproverIssues();
  } else if (panelId === 'surveys-panel') {
    loadSurveys();
  } else if (panelId === 'bulletin-panel') {
    loadBulletins();
  } else if (panelId === 'admin-panel') {
    loadAdminConfig();
  } else if (panelId === 'settings-panel') {
    // Just display panel
  }
}

// Feature: Issues & Feed
async function loadIssues() {
  const lat = document.getElementById('feed-lat').value || 23.2599;
  const lng = document.getElementById('feed-lng').value || 77.4126;
  const issuesList = document.getElementById('issues-list');
  issuesList.innerHTML = '<div class="spinner"></div>';
  
  try {
    const response = await fetch(`${API_BASE}/issues/feed?latitude=${lat}&longitude=${lng}&skip=0&limit=50`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (response.ok) {
      const data = await response.json();
      renderIssuesList(data, issuesList);
      updateFeedMapMarkers(data);
    } else {
      issuesList.innerHTML = '<div style="color: var(--danger)">Failed to load issues feed.</div>';
    }
  } catch (error) {
    issuesList.innerHTML = `<div style="color: var(--danger)">Connection error: ${error.message}</div>`;
  }
}

function renderIssuesList(issues, container) {
  if (!issues || issues.length === 0) {
    container.innerHTML = '<div class="glass-card" style="text-align: center; color: var(--text-secondary);">No active issues reported in this ward.</div>';
    return;
  }
  
  container.innerHTML = '';
  issues.forEach(issue => {
    const card = document.createElement('div');
    card.className = 'glass-card issue-card';
    
    const formattedDate = new Date(issue.createdAt).toLocaleString();
    const photoTag = issue.photoUrls && issue.photoUrls.length > 0 
      ? `<img class="issue-image" src="${issue.photoUrls[0]}" alt="Issue photo">` 
      : '';
      
    let lat = 0.0;
    let lng = 0.0;
    if (issue.issueLocation && issue.issueLocation.coordinates && issue.issueLocation.coordinates.length === 2) {
      lng = issue.issueLocation.coordinates[0];
      lat = issue.issueLocation.coordinates[1];
    } else if (issue.latitude !== undefined && issue.longitude !== undefined) {
      lat = issue.latitude;
      lng = issue.longitude;
    }

    // Render comments list
    let commentsHtml = '';
    if (issue.comments && issue.comments.length > 0) {
      commentsHtml = issue.comments.map(c => {
        const time = new Date(c.timestamp).toLocaleString();
        return `
          <div class="comment-item">
            <div class="comment-header">
              <span class="comment-author">${escapeHtml(c.userName)}</span>
              <span class="comment-time">${time}</span>
            </div>
            <div class="comment-content">${escapeHtml(c.content)}</div>
          </div>
        `;
      }).join('');
    } else {
      commentsHtml = `<div style="color: var(--text-secondary); font-size: 0.85rem; font-style: italic; padding: 0.25rem 0;">No comments yet. Be the first to comment!</div>`;
    }

    card.innerHTML = `
      <div class="issue-header">
        <h3 class="issue-title">
          <span style="font-family: monospace; font-size: 0.85rem; font-weight: 700; color: var(--accent); padding: 0.2rem 0.5rem; background: rgba(6,182,212,0.1); border: 1px solid var(--accent); border-radius: 4px; margin-right: 0.5rem;">${issue.readableIssueId || 'ISSUE'}</span>
          ${escapeHtml(issue.title)}
        </h3>
        <span class="status-badge ${issue.status.toLowerCase()}">${issue.status}</span>
      </div>
      <div class="issue-meta">
        <span>📍 Lat: ${lat.toFixed(4)}, Lng: ${lng.toFixed(4)}</span>
        <span>•</span>
        <span>📅 ${formattedDate}</span>
      </div>
      <p class="issue-body">${escapeHtml(issue.description)}</p>
      ${photoTag}
      <div class="issue-actions">
        <button class="action-btn" onclick="likeIssue('${issue.id}')">
          ❤️ <span>${issue.likeCount || 0} Likes</span>
        </button>
        ${issue.status === 'APPROVED' && userRole === 'ROLE_CITIZEN' ? 
          `<button class="btn btn-accent" style="padding: 0.4rem 0.8rem; font-size: 0.8rem;" onclick="resolveIssue('${issue.id}')">Mark Resolved</button>` : ''
        }
        ${issue.status === 'RESOLVED' && userRole === 'ROLE_CITIZEN' ? 
          `<button class="btn btn-primary" style="padding: 0.4rem 0.8rem; font-size: 0.8rem;" onclick="closeIssue('${issue.id}')">Validate & Close</button>` : ''
        }
      </div>
      <div class="issue-comments-section">
        <div class="comments-title">💬 Comments (${issue.comments ? issue.comments.length : 0})</div>
        <div class="comments-list">
          ${commentsHtml}
        </div>
        <form class="comment-form" onsubmit="submitComment(event, '${issue.id}')">
          <input type="text" class="comment-input" placeholder="Write a comment..." required>
          <button type="submit" class="btn btn-primary btn-comment-submit">Post</button>
        </form>
      </div>
    `;
    container.appendChild(card);
  });
}

async function likeIssue(issueId) {
  try {
    const response = await fetch(`${API_BASE}/issues/${issueId}/like`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (response.ok) {
      showToast('Liked issue status updated!');
      loadIssues();
    } else {
      showToast('Authentication required to like issues.', 'error');
    }
  } catch (error) {
    showToast(error.message, 'error');
  }
}

async function submitComment(event, issueId) {
  event.preventDefault();
  const form = event.target;
  const input = form.querySelector('.comment-input');
  const content = input.value.trim();
  if (!content) return;

  try {
    const response = await fetch(`${API_BASE}/issues/${issueId}/comment`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ content })
    });

    if (response.ok) {
      showToast('Comment posted successfully!');
      input.value = '';
      loadIssues();
    } else {
      showToast('Failed to post comment. Make sure you are logged in.', 'error');
    }
  } catch (error) {
    showToast('Error: ' + error.message, 'error');
  }
}
window.submitComment = submitComment;


async function resolveIssue(issueId) {
  try {
    const response = await fetch(`${API_BASE}/issues/${issueId}/resolve`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (response.ok) {
      showToast('Issue marked as resolved.');
      loadIssues();
    } else {
      showToast('Error marking issue resolved.', 'error');
    }
  } catch (error) {
    showToast(error.message, 'error');
  }
}

async function closeIssue(issueId) {
  try {
    const response = await fetch(`${API_BASE}/issues/${issueId}/close`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (response.ok) {
      showToast('Issue officially closed.');
      loadIssues();
    } else {
      showToast('Only the creator of the issue can close it.', 'error');
    }
  } catch (error) {
    showToast(error.message, 'error');
  }
}

async function handleRaiseIssueSubmit(e) {
  e.preventDefault();
  const title = document.getElementById('issue-title').value.trim();
  const description = document.getElementById('issue-desc').value.trim();
  const latitude = parseFloat(document.getElementById('issue-lat').value);
  const longitude = parseFloat(document.getElementById('issue-lng').value);
  const imgUrl = document.getElementById('issue-image-url').value.trim();
  
  const photoUrls = imgUrl ? [imgUrl] : [];

  try {
    const response = await fetch(`${API_BASE}/issues`, {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ title, description, latitude, longitude, photoUrls })
    });

    if (response.ok) {
      const data = await response.json();
      const issue = data.issue;
      const politicianName = data.politicianName;
      
      showToast(`Ticket: ${issue.readableIssueId}. Issue has been sent for approval under ${politicianName}.`);
      e.target.reset();
      switchTab('feed-panel', document.querySelector('.sidebar-link'));
    } else {
      const err = await response.text();
      showToast(err || 'Failed to raise issue', 'error');
    }
  } catch (error) {
    showToast('Failed: ' + error.message, 'error');
  }
}

// Feature: Politician / Approver Actions
async function loadApproverIssues() {
  const issuesContainer = document.getElementById('approver-issues-list');
  const surveysContainer = document.getElementById('approver-surveys-list');
  const newsContainer = document.getElementById('approver-news-list');
  
  if (issuesContainer) issuesContainer.innerHTML = '<div class="spinner"></div>';
  if (surveysContainer) surveysContainer.innerHTML = '<div class="spinner"></div>';
  if (newsContainer) newsContainer.innerHTML = '<div class="spinner"></div>';
  
  const lat = document.getElementById('feed-lat').value || 23.2599;
  const lng = document.getElementById('feed-lng').value || 77.4126;
  
  // 1. Fetch pending issues
  try {
    const response = await fetch(`${API_BASE}/issues/approver/pending?latitude=${lat}&longitude=${lng}&limit=100`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (response.ok) {
      const data = await response.json();
      renderApproverIssues(data, issuesContainer);
    } else {
      if (issuesContainer) issuesContainer.innerHTML = '<div style="color: var(--danger)">Failed to load pending issues.</div>';
    }
  } catch (error) {
    if (issuesContainer) issuesContainer.innerHTML = 'Error loading approver checklist.';
  }

  // 2. Fetch pending surveys
  try {
    const response = await fetch(`${API_BASE}/surveys/approver/pending`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (response.ok) {
      const data = await response.json();
      renderApproverSurveys(data, surveysContainer);
    } else {
      if (surveysContainer) surveysContainer.innerHTML = '<div style="color: var(--danger)">Failed to load pending surveys.</div>';
    }
  } catch (error) {
    if (surveysContainer) surveysContainer.innerHTML = 'Error loading surveys checklist.';
  }

  // 3. Fetch pending news posts
  try {
    const response = await fetch(`${API_BASE}/news/approver/pending`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (response.ok) {
      const data = await response.json();
      renderApproverNews(data, newsContainer);
    } else {
      if (newsContainer) newsContainer.innerHTML = '<div style="color: var(--danger)">Failed to load pending news.</div>';
    }
  } catch (error) {
    if (newsContainer) newsContainer.innerHTML = 'Error loading news checklist.';
  }
}

function renderApproverIssues(issues, container) {
  if (!container) return;
  if (!issues || issues.length === 0) {
    container.innerHTML = '<div class="glass-card" style="text-align: center; color: var(--text-secondary);">No active issues to review.</div>';
    return;
  }
  
  container.innerHTML = '';
  issues.forEach(issue => {
    const card = document.createElement('div');
    card.className = 'glass-card';
    
    const checklist = issue.approvalChecklist || {};
    const contentCheck = checklist['CONTENT_APPROPRIATE'] || false;
    const locCheck = checklist['LOCATION_VERIFIED'] || false;
    const duplicateCheck = checklist['DUPLICATE_CHECK_PASSED'] || false;

    card.innerHTML = `
      <div class="issue-header">
        <h3 class="issue-title">${escapeHtml(issue.title)}</h3>
        <span class="status-badge ${issue.status.toLowerCase()}">${issue.status}</span>
      </div>
      <p class="issue-body">${escapeHtml(issue.description)}</p>
      
      <div style="margin: 1rem 0; border-top: 1px solid var(--border-glass); padding-top: 1rem;">
        <h4 style="margin-bottom: 0.5rem; font-size: 0.9rem; text-transform: uppercase;">Checklist Requirements</h4>
        
        <div class="checklist-item">
          <div class="checklist-label">
            <span class="checklist-title">Content Appropriate</span>
            <span class="checklist-desc">Post matches community guidelines</span>
          </div>
          <label class="switch">
            <input type="checkbox" ${contentCheck ? 'checked' : ''} onchange="toggleChecklist('${issue.id}', 'CONTENT_APPROPRIATE', this.checked)">
            <span class="slider"></span>
          </label>
        </div>

        <div class="checklist-item">
          <div class="checklist-label">
            <span class="checklist-title">Location Verified</span>
            <span class="checklist-desc">Confirm coordinate accuracy</span>
          </div>
          <label class="switch">
            <input type="checkbox" ${locCheck ? 'checked' : ''} onchange="toggleChecklist('${issue.id}', 'LOCATION_VERIFIED', this.checked)">
            <span class="slider"></span>
          </label>
        </div>

        <div class="checklist-item">
          <div class="checklist-label">
            <span class="checklist-title">Duplicate Check Passed</span>
            <span class="checklist-desc">Ensure no duplicate issues raised</span>
          </div>
          <label class="switch">
            <input type="checkbox" ${duplicateCheck ? 'checked' : ''} onchange="toggleChecklist('${issue.id}', 'DUPLICATE_CHECK_PASSED', this.checked)">
            <span class="slider"></span>
          </label>
        </div>
      </div>
      
      <div style="display: flex; gap: 0.5rem; margin-top: 1rem;">
        <button class="btn btn-accent" style="flex: 1;" onclick="approveIssue('${issue.id}')" ${issue.status === 'APPROVED' ? 'disabled' : ''}>
          ✅ Approve Issue
        </button>
      </div>
    `;
    container.appendChild(card);
  });
}

async function toggleChecklist(issueId, checkName, checked) {
  try {
    const response = await fetch(`${API_BASE}/issues/approver/${issueId}/checklist`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ checkName, checked })
    });
    if (response.ok) {
      showToast('Checklist state synchronized!');
    } else {
      showToast('Failed to update checklist.', 'error');
    }
  } catch (error) {
    showToast(error.message, 'error');
  }
}

async function approveIssue(issueId) {
  try {
    const response = await fetch(`${API_BASE}/issues/approver/${issueId}/approve`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (response.ok) {
      showToast('Issue successfully approved for resolution!');
      loadApproverIssues();
    } else {
      const err = await response.text();
      showToast(err || 'Checklist incomplete or authorization invalid.', 'error');
    }
  } catch (error) {
    showToast(error.message, 'error');
  }
}

function renderApproverSurveys(surveys, container) {
  if (!container) return;
  if (!surveys || surveys.length === 0) {
    container.innerHTML = '<div class="glass-card" style="text-align: center; color: var(--text-secondary);">No pending surveys to review.</div>';
    return;
  }

  container.innerHTML = '';
  surveys.forEach(survey => {
    const card = document.createElement('div');
    card.className = 'glass-card';
    card.innerHTML = `
      <div class="issue-header">
        <h3 class="issue-title">🗳️ ${escapeHtml(survey.title)}</h3>
        <span class="status-badge pending">PENDING</span>
      </div>
      <p class="issue-body">${escapeHtml(survey.description)}</p>
      <div style="margin: 0.5rem 0; font-size: 0.9rem; color: var(--text-secondary);">
        <strong>Target Boundary:</strong> ${escapeHtml(survey.targetBoundaryCode)}
      </div>
      <div style="display: flex; gap: 0.5rem; margin-top: 1rem;">
        <button class="btn btn-accent" style="flex: 1;" onclick="approveSurveyForApprover('${survey.id}')">
          ✅ Approve Survey
        </button>
      </div>
    `;
    container.appendChild(card);
  });
}

async function approveSurveyForApprover(surveyId) {
  try {
    const response = await fetch(`${API_BASE}/surveys/approver/${surveyId}/approve`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (response.ok) {
      showToast('Survey successfully approved!');
      loadApproverIssues();
    } else {
      const err = await response.text();
      showToast(err || 'Failed to approve survey.', 'error');
    }
  } catch (error) {
    showToast(error.message, 'error');
  }
}

function renderApproverNews(newsList, container) {
  if (!container) return;
  if (!newsList || newsList.length === 0) {
    container.innerHTML = '<div class="glass-card" style="text-align: center; color: var(--text-secondary);">No pending news bulletins to review.</div>';
    return;
  }

  container.innerHTML = '';
  newsList.forEach(news => {
    const card = document.createElement('div');
    card.className = 'glass-card';
    const programDate = new Date(news.programDate).toLocaleString();
    card.innerHTML = `
      <div class="issue-header">
        <h3 class="issue-title">📢 News Post</h3>
        <span class="status-badge pending">PENDING</span>
      </div>
      <p class="issue-body">${escapeHtml(news.content)}</p>
      <div style="margin: 0.5rem 0; font-size: 0.9rem; color: var(--text-secondary);">
        <strong>Event Date:</strong> ${programDate} | <strong>Location:</strong> ${escapeHtml(news.locationCode)}
      </div>
      <div style="display: flex; gap: 0.5rem; margin-top: 1rem;">
        <button class="btn btn-accent" style="flex: 1;" onclick="approveNewsPostForApprover('${news.id}')">
          ✅ Approve News Post
        </button>
      </div>
    `;
    container.appendChild(card);
  });
}

async function approveNewsPostForApprover(newsId) {
  try {
    const response = await fetch(`${API_BASE}/news/approver/${newsId}/approve`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (response.ok) {
      showToast('News post successfully approved!');
      loadApproverIssues();
    } else {
      const err = await response.text();
      showToast(err || 'Failed to approve news post.', 'error');
    }
  } catch (error) {
    showToast(error.message, 'error');
  }
}

window.approveSurveyForApprover = approveSurveyForApprover;
window.approveNewsPostForApprover = approveNewsPostForApprover;

// Feature: Surveys
async function loadSurveys() {
  const container = document.getElementById('surveys-list');
  container.innerHTML = '<div class="spinner"></div>';
  
  const lat = document.getElementById('feed-lat').value || 23.2599;
  const lng = document.getElementById('feed-lng').value || 77.4126;
  
  try {
    const response = await fetch(`${API_BASE}/surveys/active?latitude=${lat}&longitude=${lng}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (response.ok) {
      const data = await response.json();
      renderSurveys(data, container);
    }
  } catch (error) {
    container.innerHTML = 'Error loading surveys.';
  }
}

function renderSurveys(surveys, container) {
  if (!surveys || surveys.length === 0) {
    container.innerHTML = '<div class="glass-card" style="text-align: center; color: var(--text-secondary);">No active public surveys at this time.</div>';
    return;
  }

  container.innerHTML = '';
  surveys.forEach(survey => {
    const card = document.createElement('div');
    card.className = 'glass-card survey-card';
    
    // Check if user has answered this survey already in local cache to toggle chart view
    const alreadyAnswered = localStorage.getItem(`survey_voted_${survey.id}`) === 'true';

    let questionsHtml = '';
    survey.questions.forEach(q => {
      questionsHtml += `
        <div class="survey-question" data-question-id="${q.id}">
          <p class="survey-question-text">${escapeHtml(q.questionText)}</p>
          <div class="survey-options" data-multi-select="${q.multiSelect || false}">
            ${q.options.map(opt => `
              <button class="option-btn" data-option="${opt}" onclick="selectSurveyOption(this)">
                ${escapeHtml(opt)}
              </button>
            `).join('')}
          </div>
        </div>
      `;
    });

    card.innerHTML = `
      <h3>
        <span style="font-family: monospace; font-size: 0.85rem; font-weight: 700; color: var(--primary); padding: 0.2rem 0.5rem; background: rgba(99,102,241,0.1); border: 1px solid var(--primary); border-radius: 4px; margin-right: 0.5rem;">${survey.readableSurveyId || 'SURVEY'}</span>
        🗳️ ${escapeHtml(survey.title)}
      </h3>
      <p style="color: var(--text-secondary); margin: 0.5rem 0 1rem 0;">${escapeHtml(survey.description)}</p>
      
      <div id="survey-qa-${survey.id}" style="display: ${alreadyAnswered ? 'none' : 'block'};">
        ${questionsHtml}
        <button class="btn btn-primary submit-survey-btn" style="margin-top: 1rem; width: 100%;" onclick="submitSurveyResponse('${survey.id}', this)">
          Submit Responses
        </button>
      </div>

      <div id="survey-chart-${survey.id}" class="chart-container" style="display: ${alreadyAnswered ? 'block' : 'none'};">
        <h4 style="margin-bottom: 1rem;">Survey sentiment analytics</h4>
        <div id="chart-bars-${survey.id}"></div>
        <button class="btn btn-secondary" style="margin-top: 1rem; width: 100%;" onclick="toggleSurveyQA('${survey.id}')">
          Change Response
        </button>
      </div>

      <button class="btn btn-accent view-sentiment-btn" style="margin-top: 1rem; width: 100%; display: ${alreadyAnswered ? 'none' : 'block'};" onclick="showSurveyReport('${survey.id}', this)">
        📊 View Real-Time Sentiment
      </button>
    `;
    container.appendChild(card);
    
    if (alreadyAnswered) {
      loadSentimentReport(survey.id);
    }
  });
}

function toggleSurveyQA(surveyId) {
  const card = document.getElementById(`survey-qa-${surveyId}`).closest('.survey-card');
  card.querySelector(`#survey-qa-${surveyId}`).style.display = 'block';
  card.querySelector(`#survey-chart-${surveyId}`).style.display = 'none';
  const submitBtn = card.querySelector('.submit-survey-btn');
  if (submitBtn) submitBtn.style.display = 'block';
  const viewSentimentBtn = card.querySelector('.view-sentiment-btn');
  if (viewSentimentBtn) viewSentimentBtn.style.display = 'block';
  localStorage.removeItem(`survey_voted_${surveyId}`);
}

function selectSurveyOption(btn) {
  const optionsContainer = btn.parentNode;
  const isMultiSelect = optionsContainer.dataset.multiSelect === 'true';
  
  if (isMultiSelect) {
    btn.classList.toggle('selected');
  } else {
    optionsContainer.querySelectorAll('.option-btn').forEach(b => b.classList.remove('selected'));
    btn.classList.add('selected');
  }
}

async function submitSurveyResponse(surveyId, submitBtn) {
  const card = submitBtn.closest('.survey-card');
  const questionsDivs = card.querySelectorAll('.survey-question');
  
  const answers = {};
  let allAnswered = true;
  
  questionsDivs.forEach(qDiv => {
    const qId = qDiv.dataset.questionId;
    const selected = qDiv.querySelectorAll('.option-btn.selected');
    if (selected.length === 0) {
      allAnswered = false;
    } else {
      answers[qId] = Array.from(selected).map(btn => btn.dataset.option);
    }
  });
  
  if (!allAnswered) {
    showToast('Please answer all questions before submitting.', 'error');
    return;
  }

  try {
    const response = await fetch(`${API_BASE}/surveys/${surveyId}/respond`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ answers })
    });

    if (response.ok) {
      showToast('Responses submitted successfully!');
      localStorage.setItem(`survey_voted_${surveyId}`, 'true');
      
      card.querySelector(`#survey-qa-${surveyId}`).style.display = 'none';
      card.querySelector(`#survey-chart-${surveyId}`).style.display = 'block';
      
      submitBtn.style.display = 'none';
      const viewSentimentBtn = card.querySelector('.view-sentiment-btn');
      if (viewSentimentBtn) viewSentimentBtn.style.display = 'none';
      
      loadSentimentReport(surveyId);
    } else {
      const err = await response.text();
      showToast(err || 'Failed to submit survey responses', 'error');
    }
  } catch (error) {
    showToast(error.message, 'error');
  }
}

async function showSurveyReport(surveyId, buttonEl) {
  const card = buttonEl.closest('.survey-card');
  card.querySelector(`#survey-qa-${surveyId}`).style.display = 'none';
  card.querySelector(`#survey-chart-${surveyId}`).style.display = 'block';
  
  const submitBtn = card.querySelector('.submit-survey-btn');
  if (submitBtn) submitBtn.style.display = 'none';
  buttonEl.style.display = 'none';
  
  loadSentimentReport(surveyId);
}

async function loadSentimentReport(surveyId) {
  const barsContainer = document.getElementById(`chart-bars-${surveyId}`);
  barsContainer.innerHTML = 'Loading Sentiment Reports...';

  try {
    const response = await fetch(`${API_BASE}/surveys/${surveyId}/lobbying-report`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (response.ok) {
      const data = await response.json();
      
      barsContainer.innerHTML = '';
      
      const agg = data.aggregatedAnswers || {};
      for (const question in agg) {
        const optionMap = agg[question];
        
        let total = 0;
        for (const opt in optionMap) total += optionMap[opt];
        
        const qGroup = document.createElement('div');
        qGroup.style.marginBottom = '1.5rem';
        qGroup.innerHTML = `<p style="font-weight: 600; margin-bottom: 0.5rem;">${question}</p>`;

        for (const opt in optionMap) {
          const count = optionMap[opt];
          const pct = total > 0 ? ((count / total) * 100).toFixed(0) : 0;
          
          const bar = document.createElement('div');
          bar.className = 'chart-bar-group';
          bar.innerHTML = `
            <div class="chart-label-row">
              <span>${opt}</span>
              <span>${count} votes (${pct}%)</span>
            </div>
            <div class="chart-bar-bg">
              <div class="chart-bar-fill" style="width: ${pct}%"></div>
            </div>
          `;
          qGroup.appendChild(bar);
        }
        barsContainer.appendChild(qGroup);
      }
    }
  } catch (error) {
    barsContainer.innerHTML = 'Error compiling sentiments.';
  }
}

async function handleCreateSurveySubmit(e) {
  e.preventDefault();
  const title = document.getElementById('survey-title').value.trim();
  const description = document.getElementById('survey-desc').value.trim();
  const targetBoundaryCode = document.getElementById('survey-boundary-code').value.trim();
  const expirationDateVal = document.getElementById('survey-expiration').value;
  
  if (!expirationDateVal) {
    showToast('Please select an expiration date & time.', 'error');
    return;
  }
  
  const expirationDate = new Date(expirationDateVal).toISOString();

  // Gather questions
  const questionBlocks = document.querySelectorAll('.question-block');
  const questions = [];
  
  questionBlocks.forEach(block => {
    const qText = block.querySelector('.q-text').value.trim();
    const qOptionsText = block.querySelector('.q-options').value.trim();
    const isMultiSelect = block.querySelector('.q-multiselect').checked;
    
    const options = qOptionsText.split(',').map(o => o.trim()).filter(Boolean);
    questions.push({
      questionText: qText,
      options: options,
      multiSelect: isMultiSelect
    });
  });

  try {
    const response = await fetch(`${API_BASE}/surveys`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({
        title,
        description,
        questions,
        targetBoundaryCode,
        expirationDate
      })
    });

    if (response.ok) {
      showToast('Survey successfully proposed. Awaiting Admin activation!');
      e.target.reset();
      
      // Re-populate with exactly one question block
      const container = document.getElementById('survey-questions-container');
      container.innerHTML = '';
      addQuestionField();
      setDefaultSurveyExpiration();
      
      loadSurveys();
    } else {
      showToast('Failed to create survey.', 'error');
    }
  } catch (error) {
    showToast(error.message, 'error');
  }
}

let questionCounter = 0;

function addQuestionField() {
  const container = document.getElementById('survey-questions-container');
  if (!container) return;
  questionCounter++;
  
  const block = document.createElement('div');
  block.className = 'question-block';
  block.id = `question-block-${questionCounter}`;
  block.innerHTML = `
    <div class="question-header">
      <span class="question-num">Question #${questionCounter}</span>
      <button type="button" class="btn-remove-q" onclick="removeQuestionField('${block.id}')">Remove</button>
    </div>
    <div class="form-group">
      <label class="form-label">Question Text</label>
      <input class="form-input q-text" type="text" placeholder="e.g. Do you support the metro extension?" required>
    </div>
    <div class="form-group">
      <label class="form-label">Options (Comma separated)</label>
      <input class="form-input q-options" type="text" placeholder="Strongly Support, Neutral, Oppose" required>
    </div>
    <div class="form-group" style="margin-bottom: 0;">
      <label class="checkbox-label">
        <input type="checkbox" class="q-multiselect">
        <span>Allow Multiple Answers (Multi-Select)</span>
      </label>
    </div>
  `;
  
  container.appendChild(block);
  updateQuestionNumbers();
}

function removeQuestionField(blockId) {
  const block = document.getElementById(blockId);
  if (block) {
    block.remove();
    updateQuestionNumbers();
  }
}

function updateQuestionNumbers() {
  const blocks = document.querySelectorAll('.question-block');
  blocks.forEach((block, index) => {
    const numSpan = block.querySelector('.question-num');
    if (numSpan) numSpan.innerText = `Question #${index + 1}`;
    
    const removeBtn = block.querySelector('.btn-remove-q');
    if (removeBtn) {
      if (blocks.length === 1) {
        removeBtn.disabled = true;
      } else {
        removeBtn.disabled = false;
      }
    }
  });
}

function setDefaultSurveyExpiration() {
  const picker = document.getElementById('survey-expiration');
  if (picker) {
    const defaultDate = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
    const year = defaultDate.getFullYear();
    const month = String(defaultDate.getMonth() + 1).padStart(2, '0');
    const day = String(defaultDate.getDate()).padStart(2, '0');
    const hours = String(defaultDate.getHours()).padStart(2, '0');
    const minutes = String(defaultDate.getMinutes()).padStart(2, '0');
    picker.value = `${year}-${month}-${day}T${hours}:${minutes}`;
  }
}

window.addQuestionField = addQuestionField;
window.removeQuestionField = removeQuestionField;
window.setDefaultSurveyExpiration = setDefaultSurveyExpiration;


// Feature: Bulletins
async function loadBulletins() {
  const container = document.getElementById('bulletin-list');
  container.innerHTML = '<div class="spinner"></div>';

  const lat = document.getElementById('feed-lat').value || 23.2599;
  const lng = document.getElementById('feed-lng').value || 77.4126;

  try {
    const response = await fetch(`${API_BASE}/news/bulletin?latitude=${lat}&longitude=${lng}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (response.ok) {
      const data = await response.json();
      renderBulletins(data, container);
    }
  } catch (error) {
    container.innerHTML = 'Error loading news bulletin board.';
  }
}

function renderBulletins(bulletins, container) {
  if (!bulletins || bulletins.length === 0) {
    container.innerHTML = '<div class="glass-card" style="text-align: center; color: var(--text-secondary);">No active program news published in this area.</div>';
    return;
  }

  container.innerHTML = '';
  bulletins.forEach(bulletin => {
    const card = document.createElement('div');
    card.className = 'glass-card bulletin-card';
    
    const programDate = new Date(bulletin.programDate).toLocaleString();
    const photoTag = bulletin.photoUrls && bulletin.photoUrls.length > 0 
      ? `<img class="issue-image" src="${bulletin.photoUrls[0]}" alt="News photo">` 
      : '';

    card.innerHTML = `
      <div class="bulletin-time">
        📅 Event Date: ${programDate}
        <span style="font-family: monospace; font-size: 0.85rem; font-weight: 700; color: var(--accent); padding: 0.2rem 0.5rem; background: rgba(6,182,212,0.1); border: 1px solid var(--accent); border-radius: 4px; float: right;">${bulletin.readableNewsId || 'NEWS'}</span>
      </div>
      <p style="font-size: 1.05rem; line-height: 1.6; margin-bottom: 1rem;">${escapeHtml(bulletin.content)}</p>
      ${photoTag}
    `;
    container.appendChild(card);
  });
}

async function handleCreateBulletinSubmit(e) {
  e.preventDefault();
  const content = document.getElementById('bulletin-content').value.trim();
  const programDateVal = document.getElementById('bulletin-date').value;
  const locationCode = document.getElementById('bulletin-location-code').value.trim() || 'WARD-01';
  
  const programDate = new Date(programDateVal).toISOString();
  // Set expiration date 2 days after program
  const expirationDate = new Date(new Date(programDate).getTime() + 2 * 24 * 60 * 60 * 1000).toISOString();

  try {
    const response = await fetch(`${API_BASE}/news/approver`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({
        content,
        programDate,
        expirationDate,
        locationCode
      })
    });

    if (response.ok) {
      showToast('Bulletin successfully broadcast to the dashboard feed!');
      e.target.reset();
      loadBulletins();
    } else {
      showToast('Failed to create bulletin.', 'error');
    }
  } catch (error) {
    showToast(error.message, 'error');
  }
}

// Feature: Administration
async function loadAdminConfig() {
  // Pull election mode
  try {
    // Simple state detection
    document.getElementById('election-mode-toggle').checked = false; // default
  } catch (e) {}

  // Pull pending surveys
  const container = document.getElementById('admin-surveys-list');
  container.innerHTML = '<div class="spinner"></div>';

  try {
    const response = await fetch(`${API_BASE}/admin/surveys/pending`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });

    if (response.ok) {
      const data = await response.json();
      renderPendingSurveys(data, container);
    }
  } catch (error) {
    container.innerHTML = 'Error loading pending surveys.';
  }
}

function renderPendingSurveys(surveys, container) {
  if (!surveys || surveys.length === 0) {
    container.innerHTML = 'No surveys pending approval.';
    return;
  }

  container.innerHTML = '';
  surveys.forEach(survey => {
    const div = document.createElement('div');
    div.className = 'checklist-item';
    div.innerHTML = `
      <div class="checklist-label">
        <span class="checklist-title">${escapeHtml(survey.title)}</span>
        <span class="checklist-desc">${escapeHtml(survey.description)}</span>
      </div>
      <button class="btn btn-accent" style="padding: 0.4rem 0.8rem; font-size: 0.8rem;" onclick="approveSurvey('${survey.id}')">
        Approve Survey
      </button>
    `;
    container.appendChild(div);
  });
}

async function approveSurvey(surveyId) {
  try {
    const response = await fetch(`${API_BASE}/admin/surveys/${surveyId}/approve`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` }
    });

    if (response.ok) {
      showToast('Survey approved and live!');
      loadAdminConfig();
    } else {
      showToast('Failed to approve survey.', 'error');
    }
  } catch (error) {
    showToast(error.message, 'error');
  }
}

async function toggleElectionMode(active) {
  try {
    const response = await fetch(`${API_BASE}/admin/config/election-mode`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ active })
    });

    if (response.ok) {
      showToast(`Election mode config set to: ${active}`);
    } else {
      showToast('Failed to toggle election mode.', 'error');
    }
  } catch (error) {
    showToast(error.message, 'error');
  }
}

// Helpers
function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
}

// Google Sign-In Setup & Handlers
function renderGoogleSignInButton() {
  const gClientId = localStorage.getItem('google_client_id') || 'YOUR_GOOGLE_CLIENT_ID';
  const container = document.getElementById("google-signin-btn");
  if (!container) return;

  // Clear previous content
  container.innerHTML = '';

  if (gClientId === 'YOUR_GOOGLE_CLIENT_ID' || gClientId.includes('placeholder') || !gClientId) {
    // Render a mock styled Google button
    container.innerHTML = `
      <div id="mock-google-btn" style="
        display: flex;
        align-items: center;
        justify-content: center;
        background-color: #ffffff;
        color: #1f1f1f;
        border: 1px solid #dadce0;
        border-radius: 4px;
        padding: 0.5rem 1rem;
        cursor: pointer;
        font-family: 'Roboto', arial, sans-serif;
        font-size: 14px;
        font-weight: 500;
        box-shadow: 0 1px 2px 0 rgba(60,64,67,0.3), 0 1px 3px 1px rgba(60,64,67,0.15);
        transition: background-color 0.2s, box-shadow 0.2s;
        user-select: none;
      ">
        <svg version="1.1" xmlns="http://www.w3.org/2000/svg" width="18px" height="18px" viewBox="0 0 48 48" style="margin-right: 10px; display: block;">
          <g>
            <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"></path>
            <path fill="#4285F4" d="M46.5 24c0-1.61-.15-3.16-.42-4.69H24v8.89h12.64c-.55 2.85-2.16 5.27-4.58 6.88l7.14 5.53c4.18-3.85 6.6-9.53 6.6-16.52z"></path>
            <path fill="#FBBC05" d="M10.54 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.98-6.19z"></path>
            <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.14-5.53c-1.98 1.32-4.52 2.13-8.75 2.13-6.26 0-11.57-4.22-13.46-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"></path>
          </g>
        </svg>
        Sign in with Google (Demo Mock)
      </div>
    `;

    document.getElementById("mock-google-btn").onclick = function() {
      handleGoogleCredentialResponse({ credential: 'mock-token-12345' });
    };
  } else {
    // Render the real Google Sign-In button
    if (window.google && window.google.accounts && window.google.accounts.id) {
      window.google.accounts.id.initialize({
        client_id: gClientId,
        callback: handleGoogleCredentialResponse
      });
      window.google.accounts.id.renderButton(
        container,
        { theme: "outline", size: "large", width: 260 }
      );
    } else {
      setTimeout(renderGoogleSignInButton, 1000);
    }
  }
}

async function handleGoogleCredentialResponse(response) {
  const idToken = response.credential;
  const gClientId = localStorage.getItem('google_client_id') || 'YOUR_GOOGLE_CLIENT_ID';

  try {
    const res = await fetch(`${API_BASE}/auth/google`, {
      method: 'POST',
      headers: {
        'Content-Type': 'text/plain',
        'X-Google-Client-ID': gClientId
      },
      body: idToken
    });

    if (res.ok) {
      const data = await res.json();
      token = data.token;
      userRole = data.role;
      userMobile = data.newUser ? 'Google User (Link Voter ID)' : 'Google User';

      localStorage.setItem('protest_token', token);
      localStorage.setItem('protest_role', userRole);
      localStorage.setItem('protest_mobile', userMobile);
      localStorage.setItem('protest_name', data.name || userMobile);

      showToast('Logged in successfully with Google!');
      document.getElementById('auth-overlay').style.display = 'none';

      if (data.newUser) {
        document.getElementById('voter-overlay').style.display = 'flex';
      } else {
        setupDashboard();
      }
    } else {
      const text = await res.text();
      showToast(text || 'Google Authentication Failed', 'error');
    }
  } catch (error) {
    showToast('Connection error during Google Auth: ' + error.message, 'error');
  }
}

// Settings Save Handler
function saveSettings(e) {
  e.preventDefault();
  const clientId = document.getElementById('setting-g-client-id').value.trim();
  const mapsKey = document.getElementById('setting-g-maps-key').value.trim();

  if (clientId) localStorage.setItem('google_client_id', clientId);
  if (mapsKey) localStorage.setItem('google_maps_key', mapsKey);

  showToast('API Settings saved successfully! Please reload the page to apply.');
}

// Maps Integration Logic (Google Maps with Leaflet Fallback)
let feedMapInstance = null;
let raiseMapInstance = null;
let feedMarker = null;
let raiseMarker = null;
let issueMarkers = [];

function initMaps() {
  const gMapsKey = localStorage.getItem('google_maps_key') || '';
  if (gMapsKey && gMapsKey !== 'YOUR_GOOGLE_MAPS_KEY') {
    loadGoogleMapsApi(gMapsKey);
  } else {
    initLeafletMaps();
  }
}

function loadGoogleMapsApi(key) {
  if (window.google && window.google.maps) {
    initGoogleMapsInstance();
    return;
  }
  const script = document.createElement('script');
  script.src = `https://maps.googleapis.com/maps/api/js?key=${key}&callback=initGoogleMapsInstance`;
  script.async = true;
  script.defer = true;
  document.head.appendChild(script);
}

window.initGoogleMapsInstance = function() {
  const defaultLat = parseFloat(document.getElementById('feed-lat').value) || 23.2599;
  const defaultLng = parseFloat(document.getElementById('feed-lng').value) || 77.4126;

  // Feed Map
  const feedDiv = document.getElementById('feed-map');
  if (feedDiv && !feedMapInstance) {
    feedMapInstance = new google.maps.Map(feedDiv, {
      center: { lat: defaultLat, lng: defaultLng },
      zoom: 13
    });
    feedMarker = new google.maps.Marker({
      position: { lat: defaultLat, lng: defaultLng },
      map: feedMapInstance,
      draggable: true
    });
    feedMarker.addListener('dragend', function(e) {
      const lat = e.latLng.lat();
      const lng = e.latLng.lng();
      document.getElementById('feed-lat').value = lat.toFixed(6);
      document.getElementById('feed-lng').value = lng.toFixed(6);
      resolveLocationName(lat, lng, "Selected on Map");
      loadIssues();
    });
    feedMapInstance.addListener('click', function(e) {
      const lat = e.latLng.lat();
      const lng = e.latLng.lng();
      feedMarker.setPosition({ lat, lng });
      document.getElementById('feed-lat').value = lat.toFixed(6);
      document.getElementById('feed-lng').value = lng.toFixed(6);
      resolveLocationName(lat, lng, "Selected on Map");
      loadIssues();
    });
  }

  // Raise Map
  const raiseDiv = document.getElementById('raise-map');
  if (raiseDiv && !raiseMapInstance) {
    raiseMapInstance = new google.maps.Map(raiseDiv, {
      center: { lat: defaultLat, lng: defaultLng },
      zoom: 13
    });
    raiseMarker = new google.maps.Marker({
      position: { lat: defaultLat, lng: defaultLng },
      map: raiseMapInstance,
      draggable: true
    });
    raiseMarker.addListener('dragend', function(e) {
      const lat = e.latLng.lat();
      const lng = e.latLng.lng();
      document.getElementById('issue-lat').value = lat.toFixed(6);
      document.getElementById('issue-lng').value = lng.toFixed(6);
    });
    raiseMapInstance.addListener('click', function(e) {
      const lat = e.latLng.lat();
      const lng = e.latLng.lng();
      raiseMarker.setPosition({ lat, lng });
      document.getElementById('issue-lat').value = lat.toFixed(6);
      document.getElementById('issue-lng').value = lng.toFixed(6);
    });
  }
};

function initLeafletMaps() {
  const defaultLat = parseFloat(document.getElementById('feed-lat').value) || 23.2599;
  const defaultLng = parseFloat(document.getElementById('feed-lng').value) || 77.4126;

  // Feed Map
  const feedDiv = document.getElementById('feed-map');
  if (feedDiv && !feedMapInstance) {
    feedMapInstance = L.map('feed-map').setView([defaultLat, defaultLng], 13);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© OpenStreetMap contributors'
    }).addTo(feedMapInstance);

    feedMarker = L.marker([defaultLat, defaultLng], { draggable: true }).addTo(feedMapInstance);
    feedMarker.on('dragend', function(e) {
      const pos = feedMarker.getLatLng();
      document.getElementById('feed-lat').value = pos.lat.toFixed(6);
      document.getElementById('feed-lng').value = pos.lng.toFixed(6);
      resolveLocationName(pos.lat, pos.lng, "Selected on Map");
      loadIssues();
    });
    feedMapInstance.on('click', function(e) {
      feedMarker.setLatLng(e.latlng);
      document.getElementById('feed-lat').value = e.latlng.lat.toFixed(6);
      document.getElementById('feed-lng').value = e.latlng.lng.toFixed(6);
      resolveLocationName(e.latlng.lat, e.latlng.lng, "Selected on Map");
      loadIssues();
    });
  }

  // Raise Map
  const raiseDiv = document.getElementById('raise-map');
  if (raiseDiv && !raiseMapInstance) {
    raiseMapInstance = L.map('raise-map').setView([defaultLat, defaultLng], 13);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© OpenStreetMap contributors'
    }).addTo(raiseMapInstance);

    raiseMarker = L.marker([defaultLat, defaultLng], { draggable: true }).addTo(raiseMapInstance);
    raiseMarker.on('dragend', function(e) {
      const pos = raiseMarker.getLatLng();
      document.getElementById('issue-lat').value = pos.lat.toFixed(6);
      document.getElementById('issue-lng').value = pos.lng.toFixed(6);
    });
    raiseMapInstance.on('click', function(e) {
      raiseMarker.setLatLng(e.latlng);
      document.getElementById('issue-lat').value = e.latlng.lat.toFixed(6);
      document.getElementById('issue-lng').value = e.latlng.lng.toFixed(6);
    });
  }
}

function updateMapLocation(lat, lng, isFeed = true) {
  if (isFeed) {
    if (feedMapInstance) {
      if (feedMapInstance.setView) {
        feedMapInstance.setView([lat, lng], 13);
      } else if (feedMapInstance.setCenter) {
        feedMapInstance.setCenter({ lat, lng });
      }
      if (feedMarker) {
        if (feedMarker.setLatLng) feedMarker.setLatLng([lat, lng]);
        else if (feedMarker.setPosition) feedMarker.setPosition({ lat, lng });
      }
    }
  } else {
    if (raiseMapInstance) {
      if (raiseMapInstance.setView) {
        raiseMapInstance.setView([lat, lng], 13);
      } else if (raiseMapInstance.setCenter) {
        raiseMapInstance.setCenter({ lat, lng });
      }
      if (raiseMarker) {
        if (raiseMarker.setLatLng) raiseMarker.setLatLng([lat, lng]);
        else if (raiseMarker.setPosition) raiseMarker.setPosition({ lat, lng });
      }
    }
  }
}

function updateFeedMapMarkers(issues) {
  // Clear old issue markers
  issueMarkers.forEach(m => {
    if (m.remove) m.remove();
    else if (m.setMap) m.setMap(null);
  });
  issueMarkers = [];

  if (!feedMapInstance) return;
  const isGoogle = (typeof google !== 'undefined' && google.maps && feedMapInstance instanceof google.maps.Map);

  issues.forEach(issue => {
    const coords = issue.issueLocation && issue.issueLocation.coordinates;
    if (coords && coords.length === 2) {
      const lng = coords[0];
      const lat = coords[1];

      if (isGoogle) {
        const marker = new google.maps.Marker({
          position: { lat, lng },
          map: feedMapInstance,
          title: issue.title
        });
        const infoWindow = new google.maps.InfoWindow({
          content: `<div style="color:#333;"><strong>${escapeHtml(issue.title)}</strong><br>Status: ${escapeHtml(issue.status)}</div>`
        });
        marker.addListener('click', () => {
          infoWindow.open(feedMapInstance, marker);
        });
        issueMarkers.push(marker);
      } else {
        const marker = L.marker([lat, lng]).addTo(feedMapInstance)
          .bindPopup(`<strong>${escapeHtml(issue.title)}</strong><br>Status: ${escapeHtml(issue.status)}`);
        issueMarkers.push(marker);
      }
    }
  });
}

async function resolveLocationName(lat, lng, prefix = "Auto-detected") {
  const statusText = document.getElementById('location-status-text');
  try {
    const response = await fetch(`${API_BASE}/issues/resolve-location?latitude=${lat}&longitude=${lng}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (response.ok) {
      const data = await response.json();
      let locationName = '';
      if (data.wardName) {
        locationName = `${data.wardName} (${data.wardCode})`;
      } else if (data.vidhanSabhaName) {
        locationName = `${data.vidhanSabhaName} (${data.vidhanSabhaCode})`;
      } else if (data.lokSabhaName) {
        locationName = `${data.lokSabhaName} (${data.lokSabhaCode})`;
      } else {
        locationName = `Out of Coverage Area (Delhi Ward-01 Only)`;
      }
      if (statusText) {
        statusText.innerText = `Location: ${prefix} - ${locationName} (Lat: ${lat.toFixed(4)}, Lng: ${lng.toFixed(4)})`;
      }
    } else {
      if (statusText) statusText.innerText = `Location: ${prefix} (Lat: ${lat.toFixed(4)}, Lng: ${lng.toFixed(4)})`;
    }
  } catch (e) {
    if (statusText) statusText.innerText = `Location: ${prefix} (Lat: ${lat.toFixed(4)}, Lng: ${lng.toFixed(4)})`;
  }
}

function detectUserLocationForFeed() {
  const statusText = document.getElementById('location-status-text');
  if (statusText) statusText.innerText = 'Detecting location...';

  if (navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(position => {
      const lat = position.coords.latitude;
      const lng = position.coords.longitude;
      document.getElementById('feed-lat').value = lat.toFixed(6);
      document.getElementById('feed-lng').value = lng.toFixed(6);
      resolveLocationName(lat, lng, "Auto-detected");
      updateMapLocation(lat, lng, true);
      loadIssues();
      showToast('Detected location successfully.');
    }, err => {
      resolveLocationName(23.2599, 77.4126, "Default fallback");
      showToast('Could not access current location: ' + err.message, 'error');
      loadIssues();
    });
  } else {
    resolveLocationName(23.2599, 77.4126, "Default fallback");
    showToast('Geolocation is not supported by your browser.', 'error');
    loadIssues();
  }
}

function locateUserForIssue() {
  if (navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(position => {
      const lat = position.coords.latitude;
      const lng = position.coords.longitude;
      document.getElementById('issue-lat').value = lat.toFixed(6);
      document.getElementById('issue-lng').value = lng.toFixed(6);
      updateMapLocation(lat, lng, false);
      showToast('Detected issue location successfully.');
    }, err => {
      showToast('Could not access location: ' + err.message, 'error');
    });
  } else {
    showToast('Geolocation is not supported by your browser.', 'error');
  }
}

async function triggerGlobalSearch() {
  const queryInput = document.getElementById('global-search-input');
  if (!queryInput) return;
  const query = queryInput.value.trim();
  if (!query) {
    showToast('Please enter an ID to search (e.g. ISSUE-000001)', 'error');
    return;
  }

  try {
    const response = await fetch(`${API_BASE}/search?query=${encodeURIComponent(query)}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });

    if (response.ok) {
      const result = await response.json();
      const type = result.type;
      const data = result.data;

      if (type === 'ISSUE') {
        switchTabWithoutReload('feed-panel', document.querySelector('[onclick*="feed-panel"]'));
        const container = document.getElementById('issues-list');
        container.innerHTML = `
          <div class="glass-card" style="margin-bottom: 1.5rem; display: flex; align-items: center; justify-content: space-between; border-color: var(--primary); width: 100%;">
            <span style="font-weight: 600; color: var(--text-primary);">Showing search result for ${escapeHtml(query.toUpperCase())}</span>
            <button class="btn btn-secondary" style="padding: 0.3rem 0.8rem; font-size: 0.85rem;" onclick="clearSearchAndReloadFeed('feed-panel')">Clear Search</button>
          </div>
        `;
        renderIssuesList([data], container);
      } else if (type === 'SURVEY') {
        switchTabWithoutReload('surveys-panel', document.querySelector('[onclick*="surveys-panel"]'));
        const container = document.getElementById('surveys-list');
        container.innerHTML = `
          <div class="glass-card" style="margin-bottom: 1.5rem; display: flex; align-items: center; justify-content: space-between; border-color: var(--primary); width: 100%;">
            <span style="font-weight: 600; color: var(--text-primary);">Showing search result for ${escapeHtml(query.toUpperCase())}</span>
            <button class="btn btn-secondary" style="padding: 0.3rem 0.8rem; font-size: 0.85rem;" onclick="clearSearchAndReloadFeed('surveys-panel')">Clear Search</button>
          </div>
        `;
        renderSurveys([data], container);
      } else if (type === 'NEWS') {
        switchTabWithoutReload('bulletin-panel', document.querySelector('[onclick*="bulletin-panel"]'));
        const container = document.getElementById('bulletin-list');
        container.innerHTML = `
          <div class="glass-card" style="margin-bottom: 1.5rem; display: flex; align-items: center; justify-content: space-between; border-color: var(--primary); width: 100%;">
            <span style="font-weight: 600; color: var(--text-primary);">Showing search result for ${escapeHtml(query.toUpperCase())}</span>
            <button class="btn btn-secondary" style="padding: 0.3rem 0.8rem; font-size: 0.85rem;" onclick="clearSearchAndReloadFeed('bulletin-panel')">Clear Search</button>
          </div>
        `;
        renderBulletins([data], container);
      }
      showToast(`Found matching ${type.toLowerCase()}!`);
    } else {
      showToast(`No item found for ID: ${query}`, 'error');
    }
  } catch (error) {
    showToast('Search failed: ' + error.message, 'error');
  }
}

function switchTabWithoutReload(panelId, linkElement) {
  document.querySelectorAll('.sidebar-link').forEach(link => link.classList.remove('active'));
  if (linkElement) linkElement.classList.add('active');

  document.querySelectorAll('.section-panel').forEach(panel => panel.classList.remove('active'));
  document.getElementById(panelId).classList.add('active');
  
  if (panelId === 'feed-panel') {
    setTimeout(() => {
      initMaps();
      if (feedMapInstance && feedMapInstance.invalidateSize) {
        feedMapInstance.invalidateSize();
      }
    }, 100);
  }
}

function clearSearchAndReloadFeed(panelId) {
  const queryInput = document.getElementById('global-search-input');
  if (queryInput) queryInput.value = '';
  
  if (panelId === 'feed-panel') {
    loadIssues();
  } else if (panelId === 'surveys-panel') {
    loadSurveys();
  } else if (panelId === 'bulletin-panel') {
    loadBulletins();
  }
}

window.triggerGlobalSearch = triggerGlobalSearch;
window.clearSearchAndReloadFeed = clearSearchAndReloadFeed;

// AI Chatbot Integration
let chatSessionId = null;

async function sendChatMessage() {
  const inputEl = document.getElementById('chat-input');
  const message = inputEl.value.trim();
  if (!message) return;

  inputEl.value = '';
  appendChatMessage(message, 'user');

  // Show typing indicator
  const messagesContainer = document.getElementById('chat-messages');
  const typingDiv = document.createElement('div');
  typingDiv.className = 'chat-message bot typing-indicator';
  typingDiv.innerHTML = '<div class="message-content"><em>AI is thinking...</em></div>';
  messagesContainer.appendChild(typingDiv);
  messagesContainer.scrollTop = messagesContainer.scrollHeight;

  // Retrieve current location
  const lat = document.getElementById('feed-lat') ? document.getElementById('feed-lat').value : 23.2599;
  const lng = document.getElementById('feed-lng') ? document.getElementById('feed-lng').value : 77.4126;

  try {
    const response = await fetch(`${API_BASE}/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({
        message,
        latitude: parseFloat(lat),
        longitude: parseFloat(lng),
        sessionId: chatSessionId
      })
    });

    // Remove typing indicator
    typingDiv.remove();

    if (response.ok) {
      const data = await response.json();
      appendChatMessage(data.reply, 'bot');
      
      // If issue was raised, refresh the feed
      if (data.reply.includes("Ticket Number:") || data.reply.includes("Submitted Successfully")) {
        if (typeof loadIssues === 'function') loadIssues();
      }
    } else {
      const err = await response.text();
      appendChatMessage('⚠️ Error contacting chatbot service: ' + (err || response.statusText), 'bot');
    }
  } catch (error) {
    if (typingDiv) typingDiv.remove();
    appendChatMessage('⚠️ Connection error: ' + error.message, 'bot');
  }
}

function appendChatMessage(text, sender) {
  const messagesContainer = document.getElementById('chat-messages');
  const messageDiv = document.createElement('div');
  messageDiv.className = `chat-message ${sender}`;
  
  const contentDiv = document.createElement('div');
  contentDiv.className = 'message-content';
  contentDiv.innerHTML = parseMarkdown(text);
  
  messageDiv.appendChild(contentDiv);
  messagesContainer.appendChild(messageDiv);
  messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

function parseMarkdown(text) {
  if (!text) return '';
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>');
}

window.sendChatMessage = sendChatMessage;

// Feature: Survey Summary Analytics Modal & Pie Charts
const CHART_COLORS = [
  '#6366f1', // Indigo
  '#10b981', // Emerald
  '#f59e0b', // Amber
  '#ec4899', // Pink
  '#06b6d4', // Cyan
  '#8b5cf6', // Violet
  '#3b82f6', // Blue
  '#ef4444'  // Red
];

window.openSurveySummaryModal = async function() {
  const modal = document.getElementById('survey-summary-modal');
  const modalBody = document.getElementById('survey-summary-modal-body');
  
  modal.classList.add('active');
  modalBody.innerHTML = '<div class="spinner"></div>';
  
  const lat = document.getElementById('feed-lat').value || 23.2599;
  const lng = document.getElementById('feed-lng').value || 77.4126;
  
  try {
    // 1. Fetch active surveys
    const response = await fetch(`${API_BASE}/surveys/active?latitude=${lat}&longitude=${lng}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    
    if (!response.ok) {
      throw new Error('Failed to load active surveys.');
    }
    
    const surveys = await response.json();
    
    if (!surveys || surveys.length === 0) {
      modalBody.innerHTML = `
        <div style="text-align: center; padding: 2.5rem 1rem; color: var(--text-secondary);">
          <span style="font-size: 3rem; display: block; margin-bottom: 1rem;">🗳️</span>
          No active local surveys found in your Ward region to analyze.
        </div>
      `;
      return;
    }
    
    // 2. Fetch lobbying/sentiment reports for each active survey in parallel
    const reports = await Promise.all(surveys.map(async survey => {
      try {
        const repRes = await fetch(`${API_BASE}/surveys/${survey.id}/lobbying-report`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (repRes.ok) {
          const report = await repRes.json();
          return { survey, report };
        }
      } catch (err) {
        console.error('Error fetching report for survey ' + survey.id, err);
      }
      return { survey, report: null };
    }));
    
    // 3. Render reports inside the modal body
    modalBody.innerHTML = '';
    
    reports.forEach(({ survey, report }) => {
      const summaryCard = document.createElement('div');
      summaryCard.className = 'survey-summary-card';
      
      const totalResponses = report ? report.totalResponses : 0;
      
      let headerHtml = `
        <div class="survey-summary-title">
          <span style="font-family: monospace; font-size: 0.8rem; font-weight: 700; color: var(--accent); padding: 0.15rem 0.4rem; background: rgba(6,182,212,0.1); border: 1px solid var(--accent); border-radius: 4px; margin-right: 0.5rem;">${survey.readableSurveyId || 'SURVEY'}</span>
          ${escapeHtml(survey.title)}
        </div>
        <div class="survey-summary-meta">
          📍 Target: <code>${escapeHtml(survey.targetBoundaryCode)}</code> &bull; 👥 Total Voters: <strong>${totalResponses}</strong>
        </div>
      `;
      
      let questionsSummaryHtml = '';
      
      if (totalResponses === 0) {
        questionsSummaryHtml = `
          <div style="text-align: center; padding: 1.5rem; color: var(--text-secondary); background: rgba(0,0,0,0.15); border-radius: 8px;">
            No responses have been submitted for this survey yet. Be the first to vote!
          </div>
        `;
      } else {
        const agg = report.aggregatedAnswers || {};
        
        survey.questions.forEach(q => {
          // Get the option-to-count map for this question from the aggregated answers
          // Note that the report maps questionText -> optionMap
          const optionMap = agg[q.questionText] || {};
          
          // Calculate total votes for this question
          let totalQuestionVotes = 0;
          q.options.forEach(opt => {
            totalQuestionVotes += optionMap[opt] || 0;
          });
          
          // Generate conic-gradient for the pie chart
          let gradientParts = [];
          let cumulativePercent = 0;
          
          if (totalQuestionVotes === 0) {
            gradientParts.push('#475569 0% 100%');
          } else {
            q.options.forEach((opt, idx) => {
              const count = optionMap[opt] || 0;
              const pct = (count / totalQuestionVotes) * 100;
              const nextPercent = cumulativePercent + pct;
              const color = CHART_COLORS[idx % CHART_COLORS.length];
              gradientParts.push(`${color} ${cumulativePercent.toFixed(1)}% ${nextPercent.toFixed(1)}%`);
              cumulativePercent = nextPercent;
            });
          }
          const gradientString = `conic-gradient(${gradientParts.join(', ')})`;
          
          // Generate legend list
          let legendHtml = '';
          q.options.forEach((opt, idx) => {
            const count = optionMap[opt] || 0;
            const pct = totalQuestionVotes > 0 ? ((count / totalQuestionVotes) * 100).toFixed(1) : '0.0';
            const color = totalQuestionVotes > 0 ? CHART_COLORS[idx % CHART_COLORS.length] : '#475569';
            legendHtml += `
              <div class="legend-item">
                <span class="legend-label">
                  <span class="legend-dot" style="background-color: ${color};"></span>
                  ${escapeHtml(opt)}
                </span>
                <span class="legend-stats"><strong>${count}</strong> votes (${pct}%)</span>
              </div>
            `;
          });
          
          questionsSummaryHtml += `
            <div class="question-summary-row">
              <div class="question-chart-container">
                <div class="pie-chart" style="background: ${gradientString};"></div>
              </div>
              <div class="question-info-container">
                <p class="question-text">${escapeHtml(q.questionText)}</p>
                <div class="legend-list">
                  ${legendHtml}
                </div>
              </div>
            </div>
          `;
        });
      }
      
      summaryCard.innerHTML = headerHtml + questionsSummaryHtml;
      modalBody.appendChild(summaryCard);
    });
    
  } catch (err) {
    modalBody.innerHTML = `
      <div style="text-align: center; padding: 2rem; color: var(--danger);">
        ⚠️ Error loading survey summary dashboard: ${escapeHtml(err.message)}
      </div>
    `;
  }
};

window.closeSurveySummaryModal = function() {
  const modal = document.getElementById('survey-summary-modal');
  modal.classList.remove('active');
};

window.closeSurveySummaryModalOnBackdrop = function(e) {
  if (e.target.id === 'survey-summary-modal') {
    closeSurveySummaryModal();
  }
};

