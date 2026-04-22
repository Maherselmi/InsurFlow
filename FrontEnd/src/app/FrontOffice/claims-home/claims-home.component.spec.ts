import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClaimsHomeComponent } from './claims-home.component';

describe('ClaimsHomeComponent', () => {
  let component: ClaimsHomeComponent;
  let fixture: ComponentFixture<ClaimsHomeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClaimsHomeComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ClaimsHomeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
