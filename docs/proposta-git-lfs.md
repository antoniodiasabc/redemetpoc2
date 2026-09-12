# Proposta: Git LFS para libs binárias

## Situação atual
- `libs/` está no `.gitignore` — JARs **não estão no GitHub**
- Git LFS **não está instalado** na máquina
- Clone em outra máquina → build **falha** (libs ausentes)

## Arquivos críticos
- `opencv-4.5.1-2.jar` — 88MB
- `libopencv_java451.so` — 58MB
- Demais JARs de `system scope` no `pom.xml` (cdm-core, grib, guava, jcommander, jdom2, joda-time, re2j, protobuf)

## Plano de execução
1. Instalar Git LFS: `sudo apt install git-lfs && git lfs install`
2. Remover `libs/` do `.gitignore`
3. Trackear binários: `git lfs track "libs/*.jar" "libs/*.so"`
4. `git add .gitattributes libs/`
5. `git commit -m "feat: adiciona libs via Git LFS"`
6. `git push`

## Resultado esperado
- `git clone` em qualquer máquina baixa os JARs via LFS automaticamente
- Build funciona sem configuração manual
