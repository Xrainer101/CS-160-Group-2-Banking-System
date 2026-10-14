const payments = [
  {
    name: "Apartment Rent",
    date: "Oct 1",
    amount: "$1,450.00",
  },
  {
    name: "Internet",
    date: "Oct 5",
    amount: "$65.00",
  },
  {
    name: "Phone",
    date: "Oct 10",
    amount: "$48.99",
  },
];

function UpcomingPayments() {
  return (
    <section className="panel">
      <div className="panel-header">
        <div>
          <p className="section-label">BILL PAY</p>
          <h2>Upcoming Payments</h2>
        </div>

        <button className="text-btn">Manage</button>
      </div>

      <div className="payments-list">
        {payments.map((payment) => (
          <div className="payment-row" key={payment.name}>
            <div className="payment-date">
              <span>{payment.date.split(" ")[0]}</span>
              <strong>{payment.date.split(" ")[1]}</strong>
            </div>

            <div className="payment-info">
              <strong>{payment.name}</strong>
              <span>Scheduled payment</span>
            </div>

            <strong className="payment-amount">
              {payment.amount}
            </strong>
          </div>
        ))}
      </div>

      <button className="primary-btn full-width">
        Schedule a Payment
      </button>
    </section>
  );
}

export default UpcomingPayments;