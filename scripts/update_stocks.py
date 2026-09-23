import json
import urllib.request
import urllib.error

# Curated pool of active liquid mid, small, and high-beta BSE/NSE stocks to evaluate daily
CANDIDATE_POOL = [
    # Clean Energy & Power
    ("SUZLON", "Renewable Energy", "Suzlon Energy"),
    ("IREDA", "Renewable Energy", "IREDA"),
    ("TATAPOWER", "Power & Utilities", "Tata Power"),
    ("NHPC", "Power & Utilities", "NHPC Ltd"),
    ("SJVN", "Power & Utilities", "SJVN Ltd"),
    ("BHEL", "Capital Goods", "BHEL"),
    
    # Banking & PSUs
    ("YESBANK", "Banking", "Yes Bank"),
    ("PNB", "Banking", "Punjab National Bank"),
    ("BANKBARODA", "Banking", "Bank of Baroda"),
    ("CANBK", "Banking", "Canara Bank"),
    ("UNIONBANK", "Banking", "Union Bank of India"),
    ("IDFCFIRSTB", "Banking", "IDFC First Bank"),
    ("FEDERALBNK", "Banking", "Federal Bank"),
    ("SOUTHBANK", "Banking", "South Indian Bank"),
    ("UCOBANK", "Banking", "UCO Bank"),
    ("MAHABANK", "Banking", "Bank of Maharashtra"),
    ("CENTRALBK", "Banking", "Central Bank of India"),
    ("IOB", "Banking", "Indian Overseas Bank"),
    ("IDBI", "Banking", "IDBI Bank"),
    ("J&KBANK", "Banking", "J&K Bank"),
    ("BANDHANBNK", "Banking", "Bandhan Bank"),
    
    # Infra, Railways & Defense
    ("IRFC", "Financial Services", "Indian Railway Finance"),
    ("HUDCO", "Financial Services", "HUDCO"),
    ("REC", "Financial Services", "REC Ltd"),
    ("PFC", "Financial Services", "Power Finance Corp"),
    ("RVNL", "Railways & Infra", "Rail Vikas Nigam"),
    ("IRCON", "Railways & Infra", "Ircon International"),
    ("RAILTEL", "Railways & Infra", "RailTel Corp"),
    ("RITES", "Railways & Infra", "RITES Ltd"),
    ("TITAGARH", "Railways & Infra", "Titagarh Rail"),
    ("TEXRAIL", "Railways & Infra", "Texmaco Rail"),
    ("BEL", "Defense & Electronics", "Bharat Electronics"),
    ("COCHINSHIP", "Defense & Shipping", "Cochin Shipyard"),
    ("GRSE", "Defense & Shipping", "Garden Reach Shipbuilders"),
    ("NBCC", "Construction", "NBCC India"),
    ("NCC", "Construction", "NCC Ltd"),
    ("GMRINFRA", "Infrastructure", "GMR Airports"),
    
    # Metals, Mining & Commodities
    ("TATASTEEL", "Metals & Mining", "Tata Steel"),
    ("SAIL", "Metals & Mining", "SAIL"),
    ("NMDC", "Metals & Mining", "NMDC Ltd"),
    ("NATIONALUM", "Metals & Mining", "National Aluminium"),
    ("VEDL", "Metals & Mining", "Vedanta Ltd"),
    ("HINDCOPPER", "Metals & Mining", "Hindustan Copper"),
    
    # Telecom, Consumer, Digital & Sugar
    ("IDEA", "Telecom", "Vodafone Idea"),
    ("ZOMATO", "Consumer Tech", "Zomato Ltd"),
    ("PAYTM", "Fintech", "Paytm"),
    ("NYKAA", "Consumer Tech", "Nykaa"),
    ("ZEEL", "Media & Entertainment", "Zee Entertainment"),
    ("TRIDENT", "Textiles", "Trident Ltd"),
    ("ALOKINDS", "Textiles", "Alok Industries"),
    ("RENUKA", "Agri & Sugar", "Shree Renuka Sugars"),
    ("BALRAMCHIN", "Agri & Sugar", "Balrampur Chini"),
    ("MOREPENLAB", "Pharma & Healthcare", "Morepen Lab"),
    ("WOCKPHARMA", "Pharma & Healthcare", "Wockhardt Ltd"),
    ("BIOCON", "Pharma & Healthcare", "Biocon Ltd"),
    ("HFCL", "Telecom & Infra", "HFCL Ltd")
]

def fetch_stock_data(symbol):
    url = f"https://query1.finance.yahoo.com/v8/finance/chart/{symbol}.NS?interval=1d&range=5d"
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            data = json.loads(response.read().decode())
            meta = data["chart"]["result"][0]["meta"]
            price = meta.get("regularMarketPrice", 0.0)
            prev_close = meta.get("chartPreviousClose", price)
            change_pct = ((price - prev_close) / prev_close) * 100.0 if prev_close > 0 else 0.0
            return price, change_pct
    except Exception:
        return None, None

def main():
    qualified = []
    
    print("Evaluating pool against previous day market close...")
    for symbol, sector, name in CANDIDATE_POOL:
        price, change_pct = fetch_stock_data(symbol)
        if price is not None and 1.0 <= price <= 500.0:
            qualified.append({
                "symbol": symbol,
                "sector": sector,
                "name": name,
                "price": price,
                "perf": change_pct
            })
    
    # Sort primarily by yesterday's performance (gainers/momentum at the top)
    qualified.sort(key=lambda x: x["perf"], reverse=True)

    # Format into stocks.json schema
    output = {
        "minPrice": 1.0,
        "maxPrice": 500.0,
        "stocks": [
            {"symbol": item["symbol"], "sector": item["sector"], "name": item["name"]}
            for item in qualified
        ]
    }

    with open("stocks.json", "w") as f:
        json.dump(output, f, indent=2)

    print(f"Successfully updated stocks.json with {len(qualified)} qualified stocks.")

if __name__ == "__main__":
    main()
