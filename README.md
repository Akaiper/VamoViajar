# VamoViajar

App Android para planejar viagens junto com amigos. Cada viagem pode ser compartilhada por um código de convite, e todo mundo que entra pode editar.

## Fase 1 (esta versão)

- Login com Google ou com e-mail e senha
- Criar, editar e apagar viagens (nome, destino, datas)
- Convidar pessoas por um código de 6 letras
- Lista de quem participa de cada viagem
- Cores do app: 6 paletas e modo claro/escuro/automático, escolhidos por cada pessoa
- Funciona offline: as mudanças sincronizam quando a internet volta

## Próximas fases

Roteiro dia a dia, checklist, reservas, gastos e divisão.

## Como gerar o APK

Todo envio para a branch `main` gera um APK automaticamente em **Actions → Gerar APK**. O APK fica no fim da página da execução, em **Artifacts**.

Precisa destes segredos em **Settings → Secrets and variables → Actions**: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`.

## Regras de segurança do Firebase

O arquivo `firestore.rules` tem as regras do banco. Sempre que ele mudar, cole o conteúdo em **Firestore Database → Regras** no console do Firebase e clique em **Publicar**.
