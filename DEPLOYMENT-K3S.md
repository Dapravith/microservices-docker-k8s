# K3s EC2 Notes

The EC2 workflow uses K3s as the lightweight Kubernetes distribution.

Use the main runbook:

```text
DEPLOYMENT.md
```

Important placement labels:

- `role=admin`: `api-gateway`, `login-service`, `mongodb`
- `role=student`: `student-service`
- `role=teacher`: `teacher-service`

No `role=frontend` label is used.
