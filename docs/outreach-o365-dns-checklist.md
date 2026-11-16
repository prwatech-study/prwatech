# Prwatech outreach — Office 365 deliverability checklist

Before setting campaign status to **RUNNING**:

1. Confirm **SPF** includes Microsoft 365 sending for `skillama.co.in`.
2. Confirm **DKIM** is enabled for the domain on Microsoft 365.
3. Publish **DMARC** (start with `p=none` if needed, move toward `quarantine`).
4. Send **owner test** from Admin → Prwatech outreach → “Send test to my email”.
5. Week 1: check O365 **quarantine** and complaint rate; pause campaign if spam spikes.
6. Default send cap **500/day**; lower in admin if Microsoft throttles (`RATE_LIMIT` bucket).