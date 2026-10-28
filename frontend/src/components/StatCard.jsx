export default function StatCard({ label, value, icon: Icon, note }) {
  return <div className="stat-card"><div className="stat-head"><span>{label}</span>{Icon && <Icon size={20}/>}</div><strong>{value}</strong>{note && <small>{note}</small>}</div>;
}
