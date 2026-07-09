# LDAP example — Keycloak User Federation

Demo cho việc Keycloak xác thực user từ một LDAP server thay vì (hoặc thêm vào)
DB nội bộ của Keycloak. Không đụng code của `bookstore-api-2` / `bookstore-fe-2` —
2 app đó chỉ redirect người dùng sang Keycloak để login (authorization code flow),
nên bất kỳ nguồn user nào Keycloak công nhận (local DB, LDAP, SAML IdP…) đều
hoạt động xuyên suốt mà không cần đổi 1 dòng code nào ở FE/API.

## Kiến trúc

```
browser → bookstore-fe-2 (3012) → bookstore-api-2 (3013) → Keycloak (8080, realm "test")
                                                                  │
                                                                  ▼
                                                       OpenLDAP (389, dc=bookstore,dc=local)
```

## 1. Khởi động OpenLDAP

```bash
docker compose up -d openldap phpldapadmin
```

- OpenLDAP: `ldap://localhost:389`, base DN `dc=bookstore,dc=local`, admin
  `cn=admin,dc=bookstore,dc=local` / `admin`.
- phpLDAPadmin (UI xem cây LDAP): http://localhost:6443 — login bằng DN admin ở trên.

## 2. Seed user mẫu

Container tự tạo DB rỗng, seed thủ công 1 lần (cơ chế auto-bootstrap của image
`osixia/openldap` không ổn định khi bind-mount trên Docker Desktop/macOS):

```bash
docker exec -i openldap ldapadd -x -H ldap://localhost \
  -D "cn=admin,dc=bookstore,dc=local" -w admin < ldap/bootstrap.ldif
```

Tạo 2 user trong `ou=people,dc=bookstore,dc=local`, mật khẩu đều là `Password123`:

| uid   | cn           | email                  |
|-------|--------------|-------------------------|
| alice | Alice Nguyen | alice@bookstore.local  |
| bob   | Bob Tran     | bob@bookstore.local    |

Và 2 group trong `ou=groups,dc=bookstore,dc=local`: `bookstore-admins` (alice),
`bookstore-users` (alice, bob).

Kiểm tra nhanh:

```bash
docker exec openldap ldapsearch -x -H ldap://localhost \
  -D "cn=admin,dc=bookstore,dc=local" -w admin \
  -b "dc=bookstore,dc=local" "(objectClass=inetOrgPerson)" uid cn mail
```

## 3. Gắn LDAP vào realm "test" (User Federation)

Vào Keycloak Admin Console (http://localhost:8080, admin/admin) →
chọn realm **test** → **User federation** → **Add Ldap providers**, điền:

| Field | Value |
|---|---|
| Console Display Name | `bookstore-ldap` |
| Vendor | Other |
| Connection URL | `ldap://openldap:389` (tên service trong docker network, không phải `localhost`) |
| Bind type | simple |
| Bind DN | `cn=admin,dc=bookstore,dc=local` |
| Bind credential | `admin` |
| Edit mode | READ_ONLY |
| Users DN | `ou=people,dc=bookstore,dc=local` |
| Username LDAP attribute | `uid` |
| RDN LDAP attribute | `uid` |
| UUID LDAP attribute | `entryUUID` |
| User object classes | `inetOrgPerson, organizationalPerson` |
| Search scope | One Level |

Bấm **Test connection** rồi **Test authentication** để xác nhận trước khi Save.
Sau khi Save, không cần bấm Sync — Keycloak import user vào lần đầu user đó login
thành công (lazy import, xem attribute `LDAP_ENTRY_DN` trên user sau khi login).

Tương đương qua Admin REST API (thay `REALM_UUID` bằng id thật của realm "test",
lấy qua `GET /admin/realms/test`, **không phải chuỗi "test"**):

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -d client_id=admin-cli -d username=admin -d password=admin -d grant_type=password \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")

REALM_UUID=$(curl -s http://localhost:8080/admin/realms/test -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")

curl -X POST http://localhost:8080/admin/realms/test/components \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "name": "bookstore-ldap",
    "providerId": "ldap",
    "providerType": "org.keycloak.storage.UserStorageProvider",
    "parentId": "'"$REALM_UUID"'",
    "config": {
      "enabled": ["true"], "priority": ["0"], "editMode": ["READ_ONLY"],
      "vendor": ["other"], "usernameLDAPAttribute": ["uid"], "rdnLDAPAttribute": ["uid"],
      "uuidLDAPAttribute": ["entryUUID"], "userObjectClasses": ["inetOrgPerson, organizationalPerson"],
      "connectionUrl": ["ldap://openldap:389"], "usersDn": ["ou=people,dc=bookstore,dc=local"],
      "authType": ["simple"], "bindDn": ["cn=admin,dc=bookstore,dc=local"], "bindCredential": ["admin"],
      "searchScope": ["1"], "trustEmail": ["true"], "importEnabled": ["true"]
    }
  }'
```

## 4. Đăng nhập thử

```bash
docker compose up -d keycloak bookstore-api-2 bookstore-fe-2
```

Mở http://localhost:3012 → Login → nhập `alice` / `Password123` trên trang
login của Keycloak → về lại bookstore-fe-2 đã đăng nhập, `preferred_username: alice`,
`email: alice@bookstore.local` lấy thẳng từ LDAP.

`test-client-2` dùng authorization code flow (không bật direct access grants),
nên không test bằng `grant_type=password` được — phải test qua trình duyệt hoặc
tạm bật `directAccessGrantsEnabled` trên client rồi tắt lại sau khi test xong.

## Ghi chú

- `editMode: READ_ONLY` — đổi mật khẩu/profile trong Keycloak Account Console sẽ
  bị từ chối vì nguồn dữ liệu gốc là LDAP. Đổi thành `WRITABLE` nếu muốn Keycloak
  ghi ngược lại LDAP.
- LDAP chạy `ldap://` (không TLS) vì đây là demo local. Production nên dùng
  `ldaps://` hoặc StartTLS.
- Muốn xoá federation: **User federation** → chọn `bookstore-ldap` → **Delete**
  (không xoá user LDAP gốc, chỉ xoá liên kết + user đã import vào Keycloak).
