# Signup lock + /health patch

## 1. app/config.py
Add registration settings:
```python
ALLOW_REGISTRATION: bool = True
INVITE_CODE: str = ""
```

## 2. app/auth/ register route
Add invite field to request model and validate:
```python
if not settings.ALLOW_REGISTRATION:
    raise HTTPException(status_code=403, detail="Registration closed")

if settings.INVITE_CODE and not hmac.compare_digest(
    (req.invite_code or ""), settings.INVITE_CODE
):
    raise HTTPException(status_code=403, detail="Invalid invite code")
```

## 3. app/main.py: health route
```python
@app.get("/health")
def health():
    return {"status": "ok"}
```

## 4. Android side
Add "Invite code" field on register screen. Send as `invite_code` in register request.

## After all friends registered
Set `ALLOW_REGISTRATION=false` in `.env`, then:
```bash
docker compose up -d
```
