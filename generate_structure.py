#!/usr/bin/env python3
"""Generate JARVIS Architecture Structure Diagram."""

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch
import numpy as np

fig, ax = plt.subplots(1, 1, figsize=(24, 18), facecolor='#0a0a1a')
ax.set_facecolor('#0a0a1a')
ax.set_xlim(0, 100)
ax.set_ylim(0, 100)
ax.set_aspect('equal')
ax.axis('off')

# Color scheme - neon tech theme
colors = {
    'android': '#00ff88',
    'backend': '#ff6b35',
    'web': '#00d4ff',
    'docs': '#ffcc00',
    'core': '#ff00ff',
    'security': '#ff3366',
    'voice': '#66ffcc',
    'ai': '#ff9900',
    'storage': '#9966ff',
    'network': '#00ffcc',
    'bg_dark': '#1a1a2e',
    'bg_medium': '#16213e',
    'text': '#ffffff',
    'text_dim': '#a0a0a0',
    'line': '#333366',
    'arrow': '#00ff88',
}

def draw_rounded_box(ax, x, y, w, h, color, alpha=0.15, linewidth=2):
    box = FancyBboxPatch((x, y), w, h, 
                          boxstyle="round,pad=0.3", 
                          facecolor=color, alpha=alpha,
                          edgecolor=color,
                          linewidth=linewidth)
    ax.add_patch(box)
    return box

def draw_module(ax, x, y, w, h, title, items, color, title_size=10):
    draw_rounded_box(ax, x, y, w, h, color, alpha=0.12, linewidth=2)
    ax.text(x + w/2, y + h - 1.5, title, ha='center', va='top',
            fontsize=title_size, fontweight='bold', color=color,
            fontfamily='monospace')
    
    for i, item in enumerate(items):
        ax.text(x + 1, y + h - 3.5 - i*1.8, f"• {item}", ha='left', va='top',
                fontsize=7.5, color=colors['text_dim'], fontfamily='monospace')

def draw_connection(ax, x1, y1, x2, y2, color=colors['arrow'], alpha=0.6):
    ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle='->', color=color, alpha=alpha, lw=1.5))

# Title
ax.text(50, 96, 'JARVIS — Cognitive Voice Assistant Architecture', 
        ha='center', va='top', fontsize=20, fontweight='bold', 
        color=colors['text'], fontfamily='monospace')
ax.text(50, 92, 'Hybrid On-Device & Cloud-Connected AI System', 
        ha='center', va='top', fontsize=11, color=colors['text_dim'], fontfamily='monospace')

# ═══════════════════════════════════════════════════════════════
# MAIN LAYERS
# ═══════════════════════════════════════════════════════════════

# Layer 1: Android App (On-Device)
draw_rounded_box(ax, 2, 50, 30, 40, colors['android'], alpha=0.08, linewidth=3)
ax.text(17, 88, '[ANDROID] On-Device Layer (Kotlin)', ha='center', va='top',
        fontsize=11, fontweight='bold', color=colors['android'], fontfamily='monospace')

draw_module(ax, 3, 75, 13.5, 9, 'Voice & Wake Word', 
            ['Wake Word (ONNX)', 'Speech-to-Text', 'Text-to-Speech'], colors['voice'])

draw_module(ax, 17.5, 75, 13.5, 9, 'UI & Overlay',
            ['Compose UI', 'Floating Widget', 'Voice Viz', 'Screens'], colors['ai'])

draw_module(ax, 3, 62, 13.5, 10, 'Device Control',
            ['System', 'App', 'Media', 'Display'], colors['core'])

draw_module(ax, 17.5, 62, 13.5, 10, 'Action Engine',
            ['Planner', 'Adapter', 'Policy', 'Execution'], colors['core'])

draw_module(ax, 3, 51, 13.5, 8, 'Local Memory',
            ['Store', 'Engine', 'Router', 'SQLite'], colors['storage'])

draw_module(ax, 17.5, 51, 13.5, 8, 'Network Layer',
            ['WebSocket', 'REST API', 'Auth', 'Protocol'], colors['network'])

# Layer 2: Backend (Cloud Brain)
draw_rounded_box(ax, 35, 40, 31, 50, colors['backend'], alpha=0.08, linewidth=3)
ax.text(50, 88, '[BACKEND] Cloud Brain (Python/FastAPI)', ha='center', va='top',
        fontsize=11, fontweight='bold', color=colors['backend'], fontfamily='monospace')

draw_module(ax, 36, 75, 14.5, 12, 'API Layer',
            ['REST Routes', 'OpenAI Compat', 'Auth Routes', 'Providers API', 'WebSocket'], colors['backend'])

draw_module(ax, 51.5, 75, 14.5, 12, 'Agent System',
            ['Orchestrator', 'Planner', 'Intent Resolver', 'Normalizer', 'Exec Models'], colors['ai'])

draw_module(ax, 36, 61, 14.5, 11, 'LLM Gateway',
            ['Provider Router', 'Circuit Breaker', 'Retry Policy', 'Registry', 'Base Provider'], colors['ai'])

draw_module(ax, 51.5, 61, 14.5, 11, 'Memory & Retrieval',
            ['Memory Manager', 'Persistent Store', 'Vector Search', 'Music Index'], colors['storage'])

draw_module(ax, 36, 48, 14.5, 10, 'Realtime',
            ['WS Manager', 'Command Registry', 'Message Router', 'Protocol', 'Redis'], colors['network'])

draw_module(ax, 51.5, 48, 14.5, 10, 'Security',
            ['Auth Manager', 'JWT Manager', 'Token Manager', 'Device Registry', 'Redaction'], colors['security'])

# Layer 3: Web App
draw_rounded_box(ax, 69, 60, 28, 30, colors['web'], alpha=0.08, linewidth=3)
ax.text(83, 88, '[WEB] Web Dashboard', ha='center', va='top',
        fontsize=11, fontweight='bold', color=colors['web'], fontfamily='monospace')

draw_module(ax, 70, 72, 12.5, 14, 'Frontend',
            ['HTML/CSS/JS', 'Voice UI', 'Config Panel', 'Real-time Status'], colors['web'])

draw_module(ax, 83.5, 72, 12.5, 14, 'Features',
            ['Web Speech', 'Chat Interface', 'Analytics', 'Settings'], colors['web'])

draw_module(ax, 70, 61, 26, 8, 'Shared',
            ['Personality Config', 'Canonical Action Schema', 'Protocol Schema'], colors['core'])

# ═══════════════════════════════════════════════════════════════
# LAYER 4: Docs & Scripts (bottom)
# ═══════════════════════════════════════════════════════════════
draw_rounded_box(ax, 2, 4, 95, 14, colors['docs'], alpha=0.05, linewidth=2)
ax.text(50, 17, '[DOCUMENTATION & DEPLOYMENT]', ha='center', va='top',
        fontsize=10, fontweight='bold', color=colors['docs'], fontfamily='monospace')

draw_module(ax, 3, 6, 14, 8, 'Docs/Architecture',
            ['Auth Architecture', 'Voice Architecture', 'API Contract', 'Execution Protocol'], colors['docs'])

draw_module(ax, 18, 6, 14, 8, 'Docs/Audit',
            ['Architecture Audit', 'Root Cause Analysis', 'Deduplication Map'], colors['docs'])

draw_module(ax, 33, 6, 14, 8, 'Docs/Deployment',
            ['Render Deployment', 'Setup Guides', 'Accessibility'], colors['docs'])

draw_module(ax, 48, 6, 14, 8, 'Docs/Testing',
            ['Voice Test Matrix', 'Feature Plans', 'Research Docs'], colors['docs'])

draw_module(ax, 63, 6, 14, 8, 'Scripts',
            ['Setup Build Env', 'Build & Verify', 'Test All', 'Run Backend'], colors['docs'])

draw_module(ax, 78, 6, 17, 8, 'Other',
            ['Wakeword Training', 'Webapp', 'Tools/Normalizer', 'Baseline/Performance'], colors['docs'])

# ═══════════════════════════════════════════════════════════════
# CONNECTIONS
# ═══════════════════════════════════════════════════════════════

# Android <-> Backend (main connection)
draw_connection(ax, 32, 70, 36, 70, color=colors['network'])
ax.text(34, 71.5, 'WS/REST', fontsize=7, color=colors['network'], fontfamily='monospace', ha='center')

draw_connection(ax, 32, 65, 36, 65, color=colors['security'])
ax.text(34, 66.5, 'Auth', fontsize=7, color=colors['security'], fontfamily='monospace', ha='center')

# Backend <-> Web
draw_connection(ax, 66, 70, 70, 70, color=colors['web'])
ax.text(68, 71.5, 'REST', fontsize=7, color=colors['web'], fontfamily='monospace', ha='center')

# Backend internal connections
draw_connection(ax, 50.5, 75, 50.5, 72, color=colors['arrow'])
draw_connection(ax, 50.5, 61, 50.5, 58, color=colors['arrow'])

# Docs connecting to all (dashed lines using plot)
ax.plot([17, 17], [17, 50], color=colors['docs'], alpha=0.3, linestyle='--', linewidth=1)
ax.plot([50, 50], [40, 17], color=colors['docs'], alpha=0.3, linestyle='--', linewidth=1)
ax.plot([83, 83], [60, 17], color=colors['docs'], alpha=0.3, linestyle='--', linewidth=1)

# ═══════════════════════════════════════════════════════════════
# LEGEND & INFO
# ═══════════════════════════════════════════════════════════════
ax.text(50, 2, 'Tech Stack: Kotlin (Android) | Python/FastAPI (Backend) | HTML/CSS/JS (Web) | Supabase | WebSocket | ONNX', 
        ha='center', va='top', fontsize=8, color=colors['text_dim'], fontfamily='monospace')

# Provider badges
providers = ['NVIDIA', 'Groq', 'OpenRouter', 'Gemini', 'Ollama']
for i, p in enumerate(providers):
    ax.text(37 + i*5.5, 42, p, fontsize=7, color=colors['backend'], 
            fontfamily='monospace', ha='center', va='center',
            bbox=dict(boxstyle='round,pad=0.2', facecolor=colors['backend'], alpha=0.2, edgecolor=colors['backend']))

ax.text(50, 44, 'Cloud LLM Providers:', ha='center', va='top', fontsize=8, color=colors['text_dim'], fontfamily='monospace')

# Mode badges
ax.text(5, 47, 'Modes:', fontsize=8, color=colors['text_dim'], fontfamily='monospace', fontweight='bold')
ax.text(12, 47, 'ONLINE', fontsize=7, color='#00ff88', fontfamily='monospace',
        bbox=dict(boxstyle='round,pad=0.2', facecolor='#00ff88', alpha=0.15, edgecolor='#00ff88'))
ax.text(20, 47, 'OFFLINE', fontsize=7, color='#ff6b35', fontfamily='monospace',
        bbox=dict(boxstyle='round,pad=0.2', facecolor='#ff6b35', alpha=0.15, edgecolor='#ff6b35'))
ax.text(29, 47, 'HINGLISH', fontsize=7, color='#ffcc00', fontfamily='monospace',
        bbox=dict(boxstyle='round,pad=0.2', facecolor='#ffcc00', alpha=0.15, edgecolor='#ffcc00'))

plt.tight_layout()
output_path = '/home/shanu/Desktop/and9/JARVIS_STRUCTURE.png'
plt.savefig(output_path, dpi=150, bbox_inches='tight', facecolor='#0a0a1a', edgecolor='none')
print(f"Structure image saved to: {output_path}")
