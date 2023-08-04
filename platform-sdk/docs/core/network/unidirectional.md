# Unidirectional network
A network where we have 2 connections per neighbor, 1 inbound and 1 outbound. A node can only initiate a protocol 
request through its outbound connection. Each peer in a connection has a role:
- **Caller** - initiated the connection, outbound for him
- **Listener** - accepted the connection, inbound for him

## Communication
A protocol is always initiated by the caller, the listener only responds:
[![](https://mermaid.ink/img/pako:eNp10LFqAzEMBuBXMZoSSF_AQ8IdLXTqkE4FL4r9pzX45KstDyXk3eujaeAo9SR-PslIF_I5gCxVfDaIx2Pk98KTE9PfzEWjjzOLmsFwNUOKHhvPKaFs_5pxMWM-bVKsCrmb4WG_H615_RJ_-EnGngzWvKGaXMxLXrln9JknsK7sPTUFdc5SsWo6wmcReP3nB9rRhDJxDH3by2Ic6QcmOLK9DDhzS-rIybXTNgdWPIWouZA9c6rYETfNyw5ktTT8otvFbur6DcsdbCU)](https://mermaid-js.github.io/mermaid-live-editor/edit/#pako:eNp10LFqAzEMBuBXMZoSSF_AQ8IdLXTqkE4FL4r9pzX45KstDyXk3eujaeAo9SR-PslIF_I5gCxVfDaIx2Pk98KTE9PfzEWjjzOLmsFwNUOKHhvPKaFs_5pxMWM-bVKsCrmb4WG_H615_RJ_-EnGngzWvKGaXMxLXrln9JknsK7sPTUFdc5SsWo6wmcReP3nB9rRhDJxDH3by2Ic6QcmOLK9DDhzS-rIybXTNgdWPIWouZA9c6rYETfNyw5ktTT8otvFbur6DcsdbCU)

### Listener state diagram
[![](https://mermaid.ink/img/pako:eNpdkLFOwzAQhl_ldCNqFkarylKQoqwMGTCD8V3AKLHL5VKpqvruOE0sCp6s___On-0L-kSMBid1yk_BfYgbq9OjjZDXAQx0Lij0ScCnGNlrSHEtu_vy_ay8xk2OGxdpYFmD28nQwn7_lUKsa7vNvz68QVXVcDALIlqUS9YZcP57DsL0x1vMC9OYmxWEPYcT033XGmCRJGWi-Zf-ZtlEKXIB23IlCtMmhjTQUuMOR5bRBcrfdVlwi_rJI1s0eUvcu3lQizZeMzofKT_7mYImQdO7YeIdulnTyzl6NCozF2j79Y26_gB77X0Y)](https://mermaid-js.github.io/mermaid-live-editor/edit/#pako:eNpdkLFOwzAQhl_ldCNqFkarylKQoqwMGTCD8V3AKLHL5VKpqvruOE0sCp6s___On-0L-kSMBid1yk_BfYgbq9OjjZDXAQx0Lij0ScCnGNlrSHEtu_vy_ay8xk2OGxdpYFmD28nQwn7_lUKsa7vNvz68QVXVcDALIlqUS9YZcP57DsL0x1vMC9OYmxWEPYcT033XGmCRJGWi-Zf-ZtlEKXIB23IlCtMmhjTQUuMOR5bRBcrfdVlwi_rJI1s0eUvcu3lQizZeMzofKT_7mYImQdO7YeIdulnTyzl6NCozF2j79Y26_gB77X0Y)

## Implementation overview
![](unidirectional-outline.png)