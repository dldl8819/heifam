update public.players
set
    mmr = mmr * 2,
    base_mmr = case
        when base_mmr is null then null
        else base_mmr * 2
    end;
