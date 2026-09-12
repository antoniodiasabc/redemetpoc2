# Consultas MongoDB — metardb

## Conectar
```bash
docker exec -it redemetpoc2_bk07042026-mongo-1 mongosh metardb
```

## Select básico
```js
// todos os documentos
db.metars.find()

// por ICAO
db.metars.find({ icao: 'SBSP' })

// por ICAO ordenado por data
db.metars.find({ icao: 'SBSP' }).sort({ _id: 1 })

// só campos específicos (projection)
db.metars.find({ icao: 'SBSP' }, { metarText: 1, horario: 1, condition: 1, _id: 0 })

// limitar resultados
db.metars.find({ icao: 'SBSP' }).sort({ _id: -1 }).limit(5)
```

## Filtros
```js
// por condição
db.metars.find({ icao: 'SBSP', condition: 'IFR' })

// com aviso
db.metars.find({ hasAviso: true })

// por intervalo de data
db.metars.find({
  createdAt: {
    $gte: ISODate('2026-09-10T13:00:00Z'),
    $lte: ISODate('2026-09-10T18:00:00Z')
  }
})

// texto contendo palavra
db.metars.find({ metarText: /TSRA/ })
```

## Agregações
```js
// contar por ICAO
db.metars.aggregate([
  { $group: { _id: '$icao', total: { $sum: 1 } } },
  { $sort: { total: -1 } }
])

// contar por condição
db.metars.aggregate([
  { $group: { _id: '$condition', total: { $sum: 1 } } }
])
```

## Utilitários
```js
// total de documentos
db.metars.countDocuments()

// total por ICAO
db.metars.countDocuments({ icao: 'SBSP' })

// ver estrutura de um documento
db.metars.findOne({ icao: 'SBSP' })

// janela de tempo dos dados
const docs = db.metars.find({icao:'SBSP'}).sort({_id:1}).toArray()
print(docs[0]._id.getTimestamp(), '→', docs[docs.length-1]._id.getTimestamp())
```

## One-liner via shell (sem entrar no mongosh)
```bash
docker exec redemetpoc2_bk07042026-mongo-1 mongosh metardb --quiet --eval \
  "db.metars.find({icao:'SBSP'}).sort({_id:-1}).limit(3).forEach(d => print(d.horario, d.condition, d.metarText))"
```
