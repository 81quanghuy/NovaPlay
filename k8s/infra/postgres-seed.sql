--
-- PostgreSQL database dump
--

\restrict gWbHrhX93frhLL3Aucarcgoq0LkYfg28GKxMyK7DlTHtosRZjnj6Mgwcox2e2dD

-- Dumped from database version 18.4 (Debian 18.4-1.pgdg13+1)
-- Dumped by pg_dump version 18.4 (Debian 18.4-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: permissions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.permissions (permission_id, created_at, created_by, updated_at, updated_by, permission_description, permission_name) FROM stdin;
\.


--
-- Data for Name: roles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.roles (role_id, created_at, created_by, updated_at, updated_by, role_description, role_name) FROM stdin;
966a4567-9ece-47d6-9445-f9bbda8870f4	2026-08-02 10:02:32.025919+00	system	2026-08-02 10:02:32.025919+00	system	Default role for normal users	USER
92c3c2db-eaf8-403b-93c4-ee78bf82ba9f	2026-08-02 10:02:32.025919+00	system	2026-08-02 10:02:32.025919+00	system	Administrator role with full permissions	ADMIN
\.


--
-- Data for Name: role_permissions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.role_permissions (role_id, permission_id) FROM stdin;
\.


--
-- Name: permissions_permission_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.permissions_permission_id_seq', 1, false);


--
-- PostgreSQL database dump complete
--

\unrestrict gWbHrhX93frhLL3Aucarcgoq0LkYfg28GKxMyK7DlTHtosRZjnj6Mgwcox2e2dD

