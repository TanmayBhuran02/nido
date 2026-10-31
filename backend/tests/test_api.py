import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.database import init_db

@pytest.fixture(autouse=True)
async def setup_test_db():
    await init_db()

@pytest.mark.asyncio
async def test_root_endpoint():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        res = await ac.get("/")
        assert res.status_code == 200
        data = res.json()
        assert data["e2ee"] is True

@pytest.mark.asyncio
async def test_auth_and_messaging_and_groups():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        import uuid
        suffix = uuid.uuid4().hex[:6]
        # Register user A
        res_a = await ac.post("/api/v1/auth/register", json={
            "username": f"alice_{suffix}",
            "password": "Password123!",
            "phone": f"+100{suffix}",
            "registration_id": 1001,
            "device_id": 1
        })
        assert res_a.status_code in (200, 201)
        token_a = res_a.json()["access_token"]
        user_a_id = res_a.json()["user_id"]

        # Register user B
        res_b = await ac.post("/api/v1/auth/register", json={
            "username": f"bob_{suffix}",
            "password": "Password123!",
            "phone": f"+200{suffix}",
            "registration_id": 1002,
            "device_id": 1
        })
        assert res_b.status_code in (200, 201)
        token_b = res_b.json()["access_token"]
        user_b_id = res_b.json()["user_id"]

        headers_a = {"Authorization": f"Bearer {token_a}"}
        headers_b = {"Authorization": f"Bearer {token_b}"}

        # User A sends pairwise message to User B
        send_res = await ac.post("/api/v1/messages/send", headers=headers_a, json={
            "recipient_user_id": user_b_id,
            "recipient_device_id": 1,
            "ciphertext": "ENCRYPTED_SIGNAL_PAYLOAD_TEST",
            "message_type": 1,
            "sender_device_id": 1
        })
        assert send_res.status_code == 200
        msg_id = send_res.json()["message_id"]

        # User B fetches pending
        pending_res = await ac.get("/api/v1/messages/pending?device_id=1", headers=headers_b)
        assert pending_res.status_code == 200
        pending_msgs = pending_res.json()
        assert any(m["id"] == msg_id for m in pending_msgs)

        # User B acks message
        ack_res = await ac.post(f"/api/v1/messages/ack/{msg_id}", headers=headers_b)
        assert ack_res.status_code == 200

        # User A creates a group with User B
        grp_res = await ac.post("/api/v1/groups", headers=headers_a, json={
            "name": "Nido Dev Group",
            "initial_members": [user_b_id]
        })
        assert grp_res.status_code == 201
        group_id = grp_res.json()["id"]

        # Send group message
        grp_send = await ac.post(f"/api/v1/groups/{group_id}/messages", headers=headers_a, json={
            "ciphertext": "ENCRYPTED_SENDER_KEY_MESSAGE",
            "message_type": 1
        })
        assert grp_send.status_code == 200

        # List user B groups
        user_b_grps = await ac.get("/api/v1/groups", headers=headers_b)
        assert user_b_grps.status_code == 200
        assert any(g["id"] == group_id for g in user_b_grps.json())

        # Test Sticker pack creation and fetch
        sticker_res = await ac.post("/api/v1/stickers/packs", headers=headers_a, json={
            "identifier": f"pack_{suffix}",
            "name": "Nido Fun Pack",
            "publisher": "Nido Music",
            "stickers": [
                {"image_file": "sticker_1.webp", "emojis": ["🎵", "🎧"]}
            ]
        })
        assert sticker_res.status_code == 201

        # User B cannot overwrite User A's sticker pack
        sticker_b_res = await ac.post("/api/v1/stickers/packs", headers=headers_b, json={
            "identifier": f"pack_{suffix}",
            "name": "Hijacked Pack",
            "publisher": "Hacker",
            "stickers": []
        })
        assert sticker_b_res.status_code == 403

        # Edit and delete 404 validations
        fake_id = "00000000-0000-0000-0000-000000000000"
        edit_404 = await ac.patch(f"/api/v1/messages/{fake_id}", headers=headers_a, json={"ciphertext": "x"})
        assert edit_404.status_code == 404

        del_404 = await ac.delete(f"/api/v1/messages/{fake_id}", headers=headers_a)
        assert del_404.status_code == 404

        # Group creation with invalid member fails 400
        bad_grp = await ac.post("/api/v1/groups", headers=headers_a, json={
            "name": "Bad Group",
            "initial_members": ["non_existent_user_id"]
        })
        assert bad_grp.status_code == 400

        # Negative group authorization tests
        # Register user C (non-member)
        res_c = await ac.post("/api/v1/auth/register", json={
            "username": f"charlie_{suffix}",
            "password": "Password123!",
            "phone": f"+300{suffix}",
            "registration_id": 1003,
            "device_id": 1
        })
        token_c = res_c.json()["access_token"]
        headers_c = {"Authorization": f"Bearer {token_c}"}

        # Non-member cannot fetch group details (403)
        c_grp_get = await ac.get(f"/api/v1/groups/{group_id}", headers=headers_c)
        assert c_grp_get.status_code == 403

        # Non-member cannot send group message (403)
        c_grp_send = await ac.post(f"/api/v1/groups/{group_id}/messages", headers=headers_c, json={
            "ciphertext": "UNAUTHORIZED_MESSAGE",
            "message_type": 1
        })
        assert c_grp_send.status_code == 403

        # Non-admin user B cannot add member (403)
        b_add_mem = await ac.post(f"/api/v1/groups/{group_id}/members", headers=headers_b, json={
            "user_id": res_c.json()["user_id"],
            "role": "MEMBER"
        })
        assert b_add_mem.status_code == 403

        # Non-admin user B cannot remove member (403)
        b_del_mem = await ac.delete(f"/api/v1/groups/{group_id}/members/{user_a_id}", headers=headers_b)
        assert b_del_mem.status_code == 403

        packs_res = await ac.get("/api/v1/stickers/packs")
        assert packs_res.status_code == 200
        assert any(p["id"] == f"pack_{suffix}" for p in packs_res.json())
