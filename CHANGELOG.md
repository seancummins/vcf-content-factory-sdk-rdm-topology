# Changelog

## 0.1.0.13

- fix(adapter): accept SDK-expanded exact-empty mode only for a fully validated fixture without a target credential object/reference; preserve strict null/ambiguous diagnostic rejection and existing absent/exact-fixture policies.

## 0.1.0.12

- fix(adapter): preserve validated fixture configuration across matching SDK partial updates and report fixed, sanitized configuration rejection reasons without changing mode selection or diagnostic publication.

## 0.1.0.11

- fix(adapter): restore the collector's public (String, Integer) dispatcher constructor, retaining lazy SDK fixture initialization and binding collection to the supplied instance identity.

## 0.1.0.10

- feat(adapter): add an explicit same-kind, one-shot read-only diagnostic dispatcher with zero publication, retaining default fixture collection and optional credential-free fixture configuration.

## 0.1.0.9

- fix(adapter): declare CanonicalLun properties under identity/storage ResourceGroups with legal individual keys, preserving full emitted property keys and all resource/relationship identities.

## 0.1.0.8

- fix(adapter): reject conflicting foreign VM ownership of one vCenter-scoped VM instance identity across CanonicalLun attachments; supersedes intermediate development build 7.

## 0.1.0.7

- feat(adapter): add version-2 qualified NAA CanonicalLun fixtures and additive local mapping-to-LUN topology, preserving build-6 fixture compatibility.

## 0.1.0.6

- fix(adapter): preserve explicitly empty nonunique ResourceKey values supported by the SDK, while rejecting null, omitted and ambiguous entries.

## 0.1.0.5

- feat(adapter): bind a strictly validated lab fixture through persisted, credential-free instance configuration.

## 0.1.0.4

- fix(test): assert the descriptor omits the CredentialKinds container.

## 0.1.0.3

- fix(adapter): explicitly mark the fixture-only adapter instance as credential-free.

## 0.1.0.2

- fix(adapter): document fixture-only operation and the explicit deployment gate.

## 0.1.0.1

- feat(adapter): add fixture-only RdmMapping foreign-VM topology proof of concept.
